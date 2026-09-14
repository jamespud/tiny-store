package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition.RoutePolicies;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition.RateLimitPolicy;

/**
 * Review round-3 P1: the policy table must be published as one immutable snapshot. The rate limiter reads it
 * on every request and treats "no policy for this route" as "pass through", so a reader that observes a
 * half-updated table (the old {@code clear()} + N {@code put}s) silently bypasses the limit.
 */
@DisplayName("gateway policy registry (round-3 P1)")
class GatewayPolicyRegistryTest {

    @Test
    @DisplayName("replaceAll publishes an immutable snapshot, not a live view of the caller's map")
    void replaceAllPublishesAnImmutableSnapshot() {
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        Map<String, RoutePolicies> source = new HashMap<>();
        source.put("order-service", policies(100));

        registry.replaceAll(source);
        // A later mutation of the source map must not leak into the registry.
        source.put("order-service", policies(1));
        source.put("inventory-service", policies(1));

        assertThat(registry.findPolicies("order-service")).isPresent();
        assertThat(registry.findPolicies("order-service").orElseThrow()
            .getRateLimit().getCapacity()).isEqualTo(100);
        assertThat(registry.findPolicies("inventory-service")).isEmpty();
    }

    @Test
    @DisplayName("a route without policies is an absent entry, not a null that breaks the snapshot")
    void routesWithoutPoliciesAreAbsentEntries() {
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        Map<String, RoutePolicies> source = new HashMap<>();
        source.put("order-service", policies(100));
        source.put("product-service", null);

        registry.replaceAll(source);

        assertThat(registry.findPolicies("order-service")).isPresent();
        assertThat(registry.findPolicies("product-service")).isEmpty();
    }

    @Test
    @DisplayName("registerPolicies(null) removes a route and an empty replaceAll empties the table")
    void registerAndReplaceEmpty() {
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        registry.replaceAll(Map.of("order-service", policies(100)));

        registry.registerPolicies("order-service", null);
        assertThat(registry.findPolicies("order-service")).isEmpty();

        registry.replaceAll(Map.of("order-service", policies(100)));
        registry.replaceAll(Map.of());
        assertThat(registry.findPolicies("order-service")).isEmpty();
    }

    @Test
    @DisplayName("a concurrent reader never observes a table with no policies at all")
    void readersNeverObserveAnEmptyTable() throws Exception {
        GatewayPolicyRegistry registry = new GatewayPolicyRegistry();
        int routes = 32;
        Map<String, RoutePolicies> before = new HashMap<>();
        Map<String, RoutePolicies> after = new HashMap<>();
        for (int i = 0; i < routes; i++) {
            before.put("route-" + i, policies(100));
            after.put("route-" + i, policies(200));
        }
        registry.replaceAll(before);

        AtomicBoolean observedEmpty = new AtomicBoolean();
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 200_000; i++) {
                if (registry.findPolicies("route-0").isEmpty()) {
                    observedEmpty.set(true);
                    return;
                }
            }
        });
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                registry.replaceAll((i & 1) == 0 ? after : before);
            }
        });
        reader.start();
        writer.start();
        writer.join();
        reader.join();

        assertThat(observedEmpty)
            .withFailMessage("a request could observe no policy for its route, which the rate limiter "
                + "treats as 'pass through' -- the refresh is not an atomic swap")
            .isFalse();
    }

    private static RoutePolicies policies(int capacity) {
        RateLimitPolicy rateLimit = new RateLimitPolicy();
        rateLimit.setType("ip");
        rateLimit.setCapacity(capacity);
        rateLimit.setRefillRate(capacity);
        RoutePolicies policies = new RoutePolicies();
        policies.setRateLimit(rateLimit);
        return policies;
    }
}
