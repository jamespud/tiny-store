package com.github.spud.tinystore.gateway.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * The compiled per-route policies (rate limit / idempotency), readable by the gateway filters on every
 * request.
 *
 * <p>Review round-3 P1: the table lives behind a single reference, so a refresh publishes it with one atomic
 * swap. The old {@code ConcurrentHashMap} was thread-safe per operation but {@code clear()} followed by N
 * {@code put}s is not a snapshot: a concurrent request could observe zero policies, and the rate limiter's
 * "no policy -> pass through" turned that window into a limit bypass. Immutable maps make a reader see either
 * the whole old table or the whole new one.
 */
@Component
public class GatewayPolicyRegistry {

	private final AtomicReference<Map<String, GatewayRoutesDefinition.RoutePolicies>> policiesByRoute =
		new AtomicReference<>(Map.of());

	public void registerPolicies(String routeId, GatewayRoutesDefinition.RoutePolicies policies) {
		policiesByRoute.updateAndGet(current -> {
			Map<String, GatewayRoutesDefinition.RoutePolicies> next = new HashMap<>(current);
			if (policies == null) {
				next.remove(routeId);
			} else {
				next.put(routeId, policies);
			}
			return Map.copyOf(next);
		});
	}

	public void clear() {
		policiesByRoute.set(Map.of());
	}

	/**
	 * Replace the whole policy set with a single reference swap (P1-6 validate-then-swap, tightened in
	 * round 3). Applying policies route by route would leave a window where a live route has no
	 * rate-limit/idempotency policy at all.
	 */
	public void replaceAll(Map<String, GatewayRoutesDefinition.RoutePolicies> policies) {
		policiesByRoute.set(immutableWithoutNulls(policies));
	}

	public Optional<GatewayRoutesDefinition.RoutePolicies> findPolicies(String routeId) {
		return Optional.ofNullable(policiesByRoute.get().get(routeId));
	}

	/**
	 * Routes are allowed to have no policies at all (the snapshot map then carries a null value), and
	 * {@link Map#copyOf} rejects nulls -- so drop them explicitly. A route without policies is the absence of
	 * an entry, which is what {@link #findPolicies} already models.
	 */
	private static Map<String, GatewayRoutesDefinition.RoutePolicies> immutableWithoutNulls(
		Map<String, GatewayRoutesDefinition.RoutePolicies> policies) {
		if (policies == null || policies.isEmpty()) {
			return Map.of();
		}
		Map<String, GatewayRoutesDefinition.RoutePolicies> copy = new HashMap<>();
		policies.forEach((routeId, policy) -> {
			if (routeId != null && policy != null) {
				copy.put(routeId, policy);
			}
		});
		return Map.copyOf(copy);
	}
}
