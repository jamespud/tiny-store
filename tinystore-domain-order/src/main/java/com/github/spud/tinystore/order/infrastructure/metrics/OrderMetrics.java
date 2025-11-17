package com.github.spud.tinystore.order.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {
    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void idempotencyConflict(String action) {
        Counter.builder("order.idempotency.conflict").tag("action", action).register(registry).increment();
    }

    public void processed(String action) {
        Counter.builder("order.action.processed").tag("action", action).register(registry).increment();
    }

    public void failure(String action) {
        Counter.builder("order.action.failure").tag("action", action).register(registry).increment();
    }
}
