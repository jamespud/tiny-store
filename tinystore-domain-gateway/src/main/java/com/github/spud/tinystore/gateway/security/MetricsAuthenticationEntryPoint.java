package com.github.spud.tinystore.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class MetricsAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	private final MeterRegistry meterRegistry;
	private final ServerAuthenticationEntryPoint delegate = new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);

	public MetricsAuthenticationEntryPoint(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, org.springframework.security.core.AuthenticationException ex) {
		meterRegistry.counter(GatewayMetrics.AUTH_FAILURES, "type", "unauthorized").increment();
		return delegate.commence(exchange, ex);
	}
}
