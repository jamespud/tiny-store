package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import reactor.core.publisher.Mono;

/**
 * Review P1-6: a gateway route refresh must validate the whole new table before touching live state, and must
 * keep the last known good table when the new one is rejected. The old order (delete routes, clear policies,
 * then compile each route) meant one bad edit could leave live routes without rate-limit/idempotency policies.
 */
@DisplayName("gateway dynamic route refresh (P1-6)")
class DynamicRouteServiceRefreshTest {

    private static final String GOOD = """
        routes:
          - id: order-service
            uri: lb://order-service
            predicates:
              - Path=/api/order/**
            filters:
              - StripPrefix=1
            retry:
              retries: 1
              methods: [GET, HEAD]
            policies:
              rateLimit:
                type: ip
                capacity: 100
                refillRate: 100
        """;

    /** The retry compiler refuses a mutation, so this table must be rejected wholesale. */
    private static final String BAD = """
        routes:
          - id: order-service
            uri: lb://order-service
            predicates:
              - Path=/api/order/**
            filters:
              - StripPrefix=1
            retry:
              retries: 1
              methods: [GET, POST]
            policies:
              rateLimit:
                type: ip
                capacity: 1
                refillRate: 1
        """;

    @Test
    @DisplayName("a rejected table keeps the previous routes and policies")
    void rejectedRefreshKeepsLastKnownGood() {
        AtomicReference<String> routes = new AtomicReference<>(GOOD);
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        when(writer.save(any())).thenReturn(Mono.empty());
        when(writer.delete(any())).thenReturn(Mono.empty());

        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        GatewayDynamicProperties properties = new GatewayDynamicProperties();
        properties.setRoutesLocation("memory:routes.yml");
        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(routes.get().getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        DynamicRouteService service = new DynamicRouteService(writer, mock(ApplicationEventPublisher.class),
            properties, loader, registry);

        // First refresh: the good table applies.
        service.refreshRoutes();
        assertThat(registry.findPolicies("order-service")).isPresent();
        verify(writer, times(1)).save(any());
        // applySnapshot clears the route id before saving it, so the good refresh is 1 delete + 1 save.
        verify(writer, times(1)).delete(any());
        assertThat(service.getLastSuccessfulRefresh()).isPresent();
        var lastGood = service.getLastSuccessfulRefresh().orElseThrow();

        // Second refresh: the bad table must be rejected before anything live is touched.
        routes.set(BAD);
        service.refreshRoutes();

        assertThat(registry.findPolicies("order-service"))
            .withFailMessage("a rejected refresh must not drop the live route's policies")
            .isPresent();
        verify(writer, times(1)).save(any());
        verify(writer, times(1)).delete(any());
        assertThat(service.getLastSuccessfulRefresh()).contains(lastGood);
    }

    @Test
    @DisplayName("round-3 P1: a writer failure while applying rolls back and is not a successful refresh")
    void applyFailureRollsBackToTheLastKnownGoodSnapshot() {
        AtomicReference<String> routes = new AtomicReference<>(GOOD);
        // Third call is the rollback re-applying the previous snapshot, so it must succeed.
        AtomicInteger saves = new AtomicInteger();
        RouteDefinitionWriter writer = mock(RouteDefinitionWriter.class);
        when(writer.save(any())).thenAnswer(invocation -> saves.incrementAndGet() == 2
            ? Mono.error(new IllegalStateException("route writer is down"))
            : Mono.empty());
        when(writer.delete(any())).thenReturn(Mono.empty());

        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        GatewayDynamicProperties properties = new GatewayDynamicProperties();
        properties.setRoutesLocation("memory:routes.yml");
        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(routes.get().getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        DynamicRouteService service = new DynamicRouteService(writer, mock(ApplicationEventPublisher.class),
            properties, loader, registry);

        service.refreshRoutes();
        assertThat(registry.findPolicies("order-service")).isPresent();
        Instant lastGood = service.getLastSuccessfulRefresh().orElseThrow();

        // Second refresh: the writer fails while the new table is being applied. The refresh must roll back
        // to the previous snapshot -- before the fix the writer error was swallowed by onErrorResume, so the
        // refresh carried on, replaced the policies and looked successful.
        service.refreshRoutes();

        assertThat(registry.findPolicies("order-service"))
            .withFailMessage("the rollback must leave the live route's policies in place")
            .isPresent();
        assertThat(service.getLastSuccessfulRefresh())
            .withFailMessage("a failed apply must not be reported as a successful refresh")
            .contains(lastGood);
        // 1 save for the first refresh, 1 failed save during the second, 1 re-apply of the previous snapshot.
        assertThat(saves.get()).isEqualTo(3);
    }
}
