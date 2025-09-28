package com.github.spud.tinystore.gateway.filter;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;

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

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class IpRateLimiterFilter implements GlobalFilter, Ordered {

	private static final String RATE_LIMIT_HEADER = "Retry-After";

	private final RedisRateLimiterService redisRateLimiterService;
	private final GatewayPolicyRegistry policyRegistry;
	private final MeterRegistry meterRegistry;

	public IpRateLimiterFilter(RedisRateLimiterService redisRateLimiterService,
		GatewayPolicyRegistry policyRegistry,
		MeterRegistry meterRegistry) {
		this.redisRateLimiterService = redisRateLimiterService;
		this.policyRegistry = policyRegistry;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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

		String identity = resolveIdentity(exchange).orElse("unknown");
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

	private Optional<String> resolveIdentity(ServerWebExchange exchange) {
		String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			return Optional.of(forwarded.split(",")[0].trim());
		}
		InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
		if (remoteAddress != null && remoteAddress.getAddress() != null) {
			return Optional.of(remoteAddress.getAddress().getHostAddress());
		}
		return Optional.empty();
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 100;
	}
}
