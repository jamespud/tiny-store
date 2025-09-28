package com.github.spud.tinystore.gateway.filter;

import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

@Component
public class RequestMetricsFilter implements GlobalFilter, Ordered {

	private final MeterRegistry meterRegistry;

	public RequestMetricsFilter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String routeId = extractRouteId(exchange).orElse("unknown");
		String method = Optional.ofNullable(exchange.getRequest().getMethod())
			.map(HttpMethod::name)
			.orElse("UNKNOWN");
		meterRegistry.counter(GatewayMetrics.REQUESTS_TOTAL, Tags.of("route", routeId, "method", method)).increment();
		Timer.Sample sample = Timer.start(meterRegistry);
		return chain.filter(exchange)
			.doFinally(signalType -> {
				recordLatency(exchange, sample, routeId, method);
			});
	}

	private void recordLatency(ServerWebExchange exchange, Timer.Sample sample, String routeId, String method) {
		var statusCodeValue = Optional.ofNullable(exchange.getResponse().getStatusCode())
			.map(httpStatusCode -> String.valueOf(httpStatusCode.value()))
			.orElse("0");
		Timer timer = Timer.builder(GatewayMetrics.REQUESTS_LATENCY)
			.tags("route", routeId, "method", method, "status", statusCodeValue)
			.register(meterRegistry);
		sample.stop(timer);
	}

	private Optional<String> extractRouteId(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		if (route != null && StringUtils.hasText(route.getId())) {
			return Optional.of(route.getId());
		}
		return Optional.empty();
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
