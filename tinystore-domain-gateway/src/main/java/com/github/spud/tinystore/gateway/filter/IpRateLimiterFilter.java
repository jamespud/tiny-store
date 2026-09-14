package com.github.spud.tinystore.gateway.filter;

import java.time.Duration;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.config.GatewayPolicyRegistry;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;
import com.github.spud.tinystore.gateway.limiter.RateLimitResult;
import com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService;
import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;
import com.github.spud.tinystore.gateway.security.ClientIpResolver;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class IpRateLimiterFilter implements GlobalFilter, Ordered {

	private static final String RATE_LIMIT_HEADER = "Retry-After";

	private final boolean rateLimitEnabled;
	private final RedisRateLimiterService redisRateLimiterService;
	private final GatewayPolicyRegistry policyRegistry;
	private final MeterRegistry meterRegistry;
	private final ClientIpResolver clientIpResolver;

	public IpRateLimiterFilter(@org.springframework.beans.factory.annotation.Value("${gateway.rate-limit.enabled:true}") boolean rateLimitEnabled,
		RedisRateLimiterService redisRateLimiterService,
		GatewayPolicyRegistry policyRegistry,
		MeterRegistry meterRegistry,
		ClientIpResolver clientIpResolver) {
		this.rateLimitEnabled = rateLimitEnabled;
		this.redisRateLimiterService = redisRateLimiterService;
		this.policyRegistry = policyRegistry;
		this.meterRegistry = meterRegistry;
		this.clientIpResolver = clientIpResolver;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (!rateLimitEnabled) {
			return chain.filter(exchange);
		}

		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		String routeId = route != null ? route.getId() : null;
		if (!StringUtils.hasText(routeId)) {
			return chain.filter(exchange);
		}

		GatewayRoutesDefinition.RoutePolicies policies = policyRegistry.findPolicies(routeId).orElse(null);
		GatewayRoutesDefinition.RateLimitPolicy policy = policies != null ? policies.getRateLimit() : null;
		if (policy == null) {
			return chain.filter(exchange);
		}

		// C16: the identity comes from the trust boundary, never straight from a client header.
		String identity = clientIpResolver.resolveIdentity(exchange).orElse("unknown");
		return redisRateLimiterService.isAllowed(routeId, identity, policy)
			.flatMap(result -> handleResult(result, routeId, identity, exchange, chain));
	}

	private Mono<Void> handleResult(RateLimitResult result, String routeId, String identity,
		ServerWebExchange exchange, GatewayFilterChain chain) {
		if (result.allowed()) {
			return chain.filter(exchange);
		}

		meterRegistry.counter(GatewayMetrics.RATE_LIMIT_REJECTED, "route", routeId, "reason", "ip")
			.increment();
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		Duration retryAfter = result.retryAfter();
		if (!retryAfter.isZero() && !retryAfter.isNegative()) {
			response.getHeaders().set(RATE_LIMIT_HEADER, String.valueOf(retryAfter.toSeconds()));
		}
		return response.setComplete();
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 100;
	}
}
