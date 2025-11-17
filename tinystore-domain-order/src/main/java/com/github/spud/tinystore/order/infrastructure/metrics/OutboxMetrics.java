package com.github.spud.tinystore.order.infrastructure.metrics;

import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final OrderOutboxEventRepository repository;

    public OutboxMetrics(MeterRegistry registry, OrderOutboxEventRepository repository) {
        this.registry = registry;
        this.repository = repository;

        // Gauge: 待发送事件数量
        Gauge.builder("order.outbox.pending", this::pendingCount)
            .description("Number of pending outbox events")
            .register(registry);

        // Gauge: 失败可重试事件数量
        Gauge.builder("order.outbox.retryable", this::retryableCount)
            .description("Number of retryable outbox events")
            .register(registry);
    }

    private double pendingCount() {
        try {
            return repository.countByStatus(OrderOutboxEventPO.OutboxEventStatus.PENDING);
        } catch (Exception e) {
            return 0d;
        }
    }

    private double retryableCount() {
        try {
            return repository.countByStatus(OrderOutboxEventPO.OutboxEventStatus.FAILED);
        } catch (Exception e) {
            return 0d;
        }
    }

    public void publishSuccess(String eventType, OffsetDateTime createdAt) {
        Counter.builder("order.outbox.publish.success")
            .tag("type", eventType)
            .register(registry)
            .increment();

        recordLatency(eventType, createdAt);
    }

    public void publishFailure(String eventType, OffsetDateTime createdAt) {
        Counter.builder("order.outbox.publish.failure")
            .tag("type", eventType)
            .register(registry)
            .increment();

        recordLatency(eventType, createdAt);
    }

    public void retryAttempt(String eventType) {
        Counter.builder("order.outbox.retry.attempt")
            .tag("type", eventType)
            .register(registry)
            .increment();
    }

    private void recordLatency(String eventType, OffsetDateTime createdAt) {
        if (createdAt == null) return;
        long millis = Duration.between(createdAt, OffsetDateTime.now()).toMillis();
        Timer.builder("order.outbox.publish.latency")
            .description("Latency from outbox created to publish (ms)")
            .tag("type", eventType)
            .register(registry)
            .record(Duration.ofMillis(Math.max(millis, 0)));
    }
}
