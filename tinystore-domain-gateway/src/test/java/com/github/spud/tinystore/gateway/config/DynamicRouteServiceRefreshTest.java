package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
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

    /** GOOD plus two extra routes: the table that exposes a partial apply. */
    private static final String NEW_ROUTES = GOOD + """
          - id: product-new
            uri: lb://product-service
            predicates:
              - Path=/api/products/**
            policies:
              rateLimit:
                type: ip
                capacity: 50
                refillRate: 50
          - id: payment-new
            uri: lb://payment-service
            predicates:
              - Path=/api/payment/**
            policies:
              rateLimit:
                type: ip
                capacity: 50
                refillRate: 50
        """;

    /** A route table with no routes at all. */
    private static final String EMPTY = "routes: []\n";

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

    @Test
    @DisplayName("round-4 P1: routes added by a partially applied table are removed by the rollback")
    void partialApplyWithNewRoutesRollsBackCompletely() {
        AtomicReference<String> routes = new AtomicReference<>(GOOD);
        StatefulRouteDefinitionWriter writer = new StatefulRouteDefinitionWriter();
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        DynamicRouteService service = service(writer, routes, registry);

        service.refreshRoutes();
        assertThat(writer.routeIds()).containsExactly("order-service");
        Instant lastGood = service.getLastSuccessfulRefresh().orElseThrow();

        // Second refresh adds two routes and fails while writing the last one: order-service and product-new
        // have already been written when payment-new throws.
        routes.set(NEW_ROUTES);
        writer.failOnSave = "payment-new";
        service.refreshRoutes();

        assertThat(writer.routeIds())
            .withFailMessage("a route written by the failed attempt must not survive the rollback")
            .containsExactly("order-service");
        assertThat(registry.findPolicies("order-service")).isPresent();
        assertThat(registry.findPolicies("product-new"))
            .withFailMessage("the restored policy table must not describe routes that are not live")
            .isEmpty();
        assertThat(service.getLastSuccessfulRefresh()).contains(lastGood);
    }

    @Test
    @DisplayName("round-4 P1: an empty table is applied through the same state machine")
    void emptyTableIsAppliedThroughTheStateMachine() {
        AtomicReference<String> routes = new AtomicReference<>(GOOD);
        StatefulRouteDefinitionWriter writer = new StatefulRouteDefinitionWriter();
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        DynamicRouteService service = service(writer, routes, registry);

        service.refreshRoutes();
        assertThat(writer.routeIds()).containsExactly("order-service");

        routes.set(EMPTY);
        service.refreshRoutes();

        assertThat(writer.routeIds()).isEmpty();
        assertThat(registry.findPolicies("order-service")).isEmpty();
        assertThat(service.getLastSuccessfulRefresh()).isPresent();
    }

    private static DynamicRouteService service(RouteDefinitionWriter writer,
        java.util.concurrent.atomic.AtomicReference<String> routes, GatewayPolicyRegistry registry) {
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
        return new DynamicRouteService(writer, mock(ApplicationEventPublisher.class), properties, loader,
            registry);
    }

    /**
     * A writer that behaves like the real one: it holds a route map, so a test can assert what the gateway
     * would actually serve after a failed refresh instead of counting calls.
     */
    private static final class StatefulRouteDefinitionWriter implements RouteDefinitionWriter {

        private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();

        private String failOnSave;

        @Override
        public Mono<Void> save(Mono<RouteDefinition> route) {
            return route.flatMap(definition -> {
                if (definition.getId().equals(failOnSave)) {
                    return Mono.error(new IllegalStateException("route writer is down"));
                }
                routes.put(definition.getId(), definition);
                return Mono.empty();
            });
        }

        @Override
        public Mono<Void> delete(Mono<String> routeId) {
            return routeId.doOnNext(routes::remove).then();
        }

        private java.util.Set<String> routeIds() {
            return routes.keySet();
        }
    }
}
