package com.github.spud.tinystore.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class MetricsAccessDeniedHandler implements ServerAccessDeniedHandler {

	private final MeterRegistry meterRegistry;
	private final ServerAccessDeniedHandler delegate = new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN);

	public MetricsAccessDeniedHandler(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, org.springframework.security.access.AccessDeniedException denied) {
		meterRegistry.counter(GatewayMetrics.AUTH_FAILURES, "type", "forbidden").increment();
		return delegate.handle(exchange, denied);
	}
}
