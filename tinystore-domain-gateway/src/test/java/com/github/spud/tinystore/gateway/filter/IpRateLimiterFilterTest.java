package com.github.spud.tinystore.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.config.GatewayPolicyRegistry;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;
import com.github.spud.tinystore.gateway.config.GatewayTrustedProxyProperties;
import com.github.spud.tinystore.gateway.limiter.RateLimitResult;
import com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService;
import com.github.spud.tinystore.gateway.security.ClientIpResolver;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * C16 at the point where it matters: the identity handed to the (cluster-shared) token bucket.
 *
 * <p>The pre-fix bug was not in the limiter but in what it was keyed on -- {@code routeId + identity}
 * where identity came straight from {@code X-Forwarded-For}. So these tests assert the *argument*,
 * which is exactly what a rotating header used to be able to change.
 */
@DisplayName("ip rate limiter identity")
class IpRateLimiterFilterTest {

    private static final String CLIENT = "203.0.113.9";
    private static final String ROUTE_ID = "order-service";

    private RedisRateLimiterService limiter;
    private GatewayPolicyRegistry policyRegistry;

    @BeforeEach
    void setUp() {
        limiter = mock(RedisRateLimiterService.class);
        policyRegistry = new GatewayPolicyRegistry();
        GatewayRoutesDefinition.RoutePolicies policies = new GatewayRoutesDefinition.RoutePolicies();
        GatewayRoutesDefinition.RateLimitPolicy policy = new GatewayRoutesDefinition.RateLimitPolicy();
        policy.setCapacity(100);
        policy.setRefillRate(100);
        policies.setRateLimit(policy);
        policyRegistry.registerPolicies(ROUTE_ID, policies);
    }

    @Test
    @DisplayName("a rotating X-Forwarded-For does not change the identity of an untrusted caller")
    void spoofedHeaderDoesNotChooseTheIdentity() {
        IpRateLimiterFilter filter = filter(new ClientIpResolver(trustedProxies()));
        when(limiter.isAllowed(anyString(), anyString(), any())).thenReturn(Mono.just(RateLimitResult.allowed(1)));

        for (String forged : List.of(CLIENT, "10.0.0.1", "198.51.100.4")) {
            StepVerifier.create(filter.filter(exchange("198.51.100.7", forged), chain())).verifyComplete();
        }

        verify(limiter, times(3)).isAllowed(eq(ROUTE_ID), eq("198.51.100.7"), any());
        verify(limiter, never()).isAllowed(anyString(), eq(CLIENT), any());
    }

    @Test
    @DisplayName("behind a trusted proxy the forwarded client address is the identity")
    void trustedProxyIdentityIsTheForwardedClient() {
        IpRateLimiterFilter filter = filter(new ClientIpResolver(trustedProxies("10.0.0.0/8")));
        when(limiter.isAllowed(anyString(), anyString(), any())).thenReturn(Mono.just(RateLimitResult.allowed(1)));

        StepVerifier.create(filter.filter(exchange("10.0.0.5", "1.2.3.4, " + CLIENT), chain()))
            .verifyComplete();

        verify(limiter).isAllowed(eq(ROUTE_ID), eq(CLIENT), any());
    }

    @Test
    @DisplayName("a rejected request is a 429 with Retry-After, a rejected bucket is not")
    void rejectionBecomesTooManyRequests() {
        IpRateLimiterFilter filter = filter(new ClientIpResolver(trustedProxies()));
        when(limiter.isAllowed(anyString(), anyString(), any()))
            .thenReturn(Mono.just(RateLimitResult.rejected(java.time.Duration.ofSeconds(2), false)));

        ServerWebExchange exchange = exchange("198.51.100.7", null);
        StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("2");
    }

    @Test
    @DisplayName("a route without a rate-limit policy never consults the limiter")
    void routesWithoutPolicyAreUntouched() {
        IpRateLimiterFilter filter = filter(new ClientIpResolver(trustedProxies()));

        StepVerifier.create(filter.filter(exchange("198.51.100.7", CLIENT, "no-such-route"), chain()))
            .verifyComplete();

        verify(limiter, never()).isAllowed(anyString(), anyString(), any());
    }

    private IpRateLimiterFilter filter(ClientIpResolver resolver) {
        return new IpRateLimiterFilter(true, limiter, policyRegistry, new SimpleMeterRegistry(), resolver);
    }

    private static org.springframework.cloud.gateway.filter.GatewayFilterChain chain() {
        return filtered -> Mono.empty();
    }

    private ServerWebExchange exchange(String peer, String forwardedFor) {
        return exchange(peer, forwardedFor, ROUTE_ID);
    }

    private ServerWebExchange exchange(String peer, String forwardedFor, String routeId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/order/trades")
            .remoteAddress(new InetSocketAddress(peer, 51234));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        ServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            Route.async().id(routeId).uri("lb://" + routeId).predicate(ex -> true).build());
        return exchange;
    }

    private static GatewayTrustedProxyProperties trustedProxies(String... proxies) {
        GatewayTrustedProxyProperties properties = new GatewayTrustedProxyProperties();
        properties.setTrustedProxies(List.of(proxies));
        return properties;
    }
}
