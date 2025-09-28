package com.github.spud.tinystore.gateway.metrics;

public final class GatewayMetrics {

	private GatewayMetrics() {
	}

	public static final String REQUESTS_TOTAL = "tinystore.gateway.requests.total";
	public static final String REQUESTS_LATENCY = "tinystore.gateway.requests.latency";
	public static final String RATE_LIMIT_REJECTED = "tinystore.gateway.ratelimit.rejected";
	public static final String IDEMPOTENCY_CONFLICT = "tinystore.gateway.idempotency.conflict";
	public static final String AUTH_FAILURES = "tinystore.gateway.auth.failures";
}
