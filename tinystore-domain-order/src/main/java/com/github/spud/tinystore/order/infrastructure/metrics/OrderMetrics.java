package com.github.spud.tinystore.order.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

	private final MeterRegistry registry;
	private final Counter idempHit;
	private final Counter idempMiss;
	private final Counter smFailure;
	private final Timer outboxLatency;

	public OrderMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.idempHit = Counter.builder("order_idempotency_hit_total").register(registry);
		this.idempMiss = Counter.builder("order_idempotency_miss_total").register(registry);
		this.smFailure = Counter.builder("order_state_machine_failure_total").register(registry);
		this.outboxLatency = Timer.builder("order_outbox_publish_latency").register(registry);
	}

	public void incrementIdempotencyHit(String key) {
		idempHit.increment();
	}

	public void incrementIdempotencyMiss(String key) {
		idempMiss.increment();
	}

	public void recordOutboxLatency(long millis) {
		outboxLatency.record(java.time.Duration.ofMillis(millis));
	}

	public void incrementHttpError(int statusCode) {
		Counter.builder("order_http_error_total").tag("status", String.valueOf(statusCode))
			.register(registry).increment();
	}

	public void idempotencyConflict(String action) {
		Counter.builder("order.idempotency.conflict").tag("action", action).register(registry)
			.increment();
	}

	public void processed(String action) {
		Counter.builder("order.action.processed").tag("action", action).register(registry).increment();
	}

	public void failure(String action) {
		Counter.builder("order.action.failure").tag("action", action).register(registry).increment();
	}

	public void incrementStateMachineFailure() {
		// 专用状态机失败计数
		this.smFailure.increment();
	}
}
