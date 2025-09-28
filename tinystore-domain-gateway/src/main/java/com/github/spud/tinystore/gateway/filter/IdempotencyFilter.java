package com.github.spud.tinystore.gateway.filter;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.config.GatewayPolicyRegistry;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyResult;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyService;
import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class IdempotencyFilter implements GlobalFilter, Ordered {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final GatewayPolicyRegistry policyRegistry;
	private final IdempotencyService idempotencyService;
	private final MeterRegistry meterRegistry;

	public IdempotencyFilter(GatewayPolicyRegistry policyRegistry,
		IdempotencyService idempotencyService,
		MeterRegistry meterRegistry) {
		this.policyRegistry = policyRegistry;
		this.idempotencyService = idempotencyService;
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
		GatewayRoutesDefinition.IdempotencyPolicy policy = policies != null ? policies.getIdempotency() : null;
		if (policy == null || !policy.isEnabled()) {
			return chain.filter(exchange);
		}

		String header = exchange.getRequest().getHeaders().getFirst(IDEMPOTENCY_HEADER);
		if (!StringUtils.hasText(header)) {
			return respondMissingKey(exchange);
		}

		String finalKey = buildKey(routeId, header);
		return idempotencyService.tryAcquire(finalKey)
			.flatMap(result -> handleResult(result, finalKey, routeId, exchange, chain));
	}

	private Mono<Void> handleResult(IdempotencyResult result, String key, String routeId, ServerWebExchange exchange,
		GatewayFilterChain chain) {
		if (!result.acquired()) {
			meterRegistry.counter(GatewayMetrics.IDEMPOTENCY_CONFLICT, "route", routeId != null ? routeId : "unknown")
				.increment();
			return respondConflict(exchange);
		}

		return chain.filter(exchange)
			.then(idempotencyService.markSuccess(key))
			.onErrorResume(ex -> idempotencyService.release(key).then(Mono.error(ex)));
	}

	private String buildKey(String routeId, String headerKey) {
		return routeId + ':' + headerKey;
	}

	private Mono<Void> respondMissingKey(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] bytes = "{\"error\":\"idempotency_key_required\"}".getBytes(StandardCharsets.UTF_8);
		return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
	}

	private Mono<Void> respondConflict(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] bytes = "{\"error\":\"idempotent_conflict\",\"message\":\"Duplicate request\"}".getBytes(StandardCharsets.UTF_8);
		return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 90;
	}
}
