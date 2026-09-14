package com.github.spud.tinystore.gateway.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.cloud.gateway.filter.FilterDefinition;

/**
 * Compiles a route from our route YAML into the framework's filter definitions.
 *
 * <p>Two rules matter for the E1 read-failover guarantee, and both are enforced here rather than in
 * a comment:
 *
 * <ul>
 * <li>The retry filter is emitted LAST, i.e. after any path rewrite. Route filters are ordered by
 * their index, so a {@code Retry} that ran before {@code StripPrefix} would re-run the rewrite on
 * every retry and strip the prefix a second time (404 downstream).</li>
 * <li>The retried methods may not include a mutation. {@code RetryGatewayFilterFactory} checks
 * {@code methods} on both the status-code and the exception path, so listing POST/PUT/PATCH/DELETE
 * would replay business writes -- a different failure model that needs its own proof (replay of the
 * committed response for the same idempotency key) before it can be enabled.</li>
 * </ul>
 */
final class GatewayRouteFilters {

	/** Methods that must never be replayed by the gateway. */
	private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

	private GatewayRouteFilters() {
	}

	static List<FilterDefinition> compile(GatewayRoutesDefinition.RouteDefinition route) {
		List<FilterDefinition> filters = new ArrayList<>();
		if (route.getFilters() != null) {
			for (String filter : route.getFilters()) {
				if (filter.startsWith("Retry=")) {
					throw new IllegalStateException("Route " + route.getId() + " declares retry twice: as the "
						+ "`retry:` policy and as a filters entry `" + filter + "`. Keep the policy, which is "
						+ "the form the route-contract test can check.");
				}
				filters.add(new FilterDefinition(filter));
			}
		}
		if (route.getRetry() != null) {
			filters.add(retryFilter(route.getId(), route.getRetry()));
		}
		return filters;
	}

	private static FilterDefinition retryFilter(String routeId, GatewayRoutesDefinition.RouteRetry retry) {
		if (retry.getRetries() < 1) {
			throw new IllegalStateException("Route " + routeId + " sets retry.retries=" + retry.getRetries()
				+ "; the framework rejects this and failover would not happen at all");
		}
		List<String> methods = retry.getMethods() == null ? List.of()
			: retry.getMethods().stream().filter(method -> method != null && !method.isBlank())
				.map(method -> method.trim().toUpperCase(Locale.ROOT)).toList();
		if (methods.isEmpty()) {
			throw new IllegalStateException("Route " + routeId + " sets a retry policy without methods; "
				+ "the retry must be limited to the methods it is safe for");
		}
		for (String method : methods) {
			if (MUTATING_METHODS.contains(method)) {
				throw new IllegalStateException("Route " + routeId + " lists " + method + " as retryable. "
					+ "A retried mutation is replayed without proof that the client can absorb it; enable "
					+ "that deliberately, with a same-key replay test, not as a side effect of read failover.");
			}
		}

		// The framework's defaults are kept for everything not named here: exceptions are
		// IOException + gateway TimeoutException (ConnectException / ConnectTimeoutException /
		// connection reset are all IOExceptions, i.e. exactly the E1 failure mode) and series is 5xx,
		// which is the standard "a failed read is worth one attempt elsewhere" semantic.
		Map<String, String> args = new LinkedHashMap<>();
		args.put("retries", String.valueOf(retry.getRetries()));
		args.put("methods", String.join(",", methods));

		FilterDefinition definition = new FilterDefinition();
		definition.setName("Retry");
		definition.setArgs(args);
		return definition;
	}
}
