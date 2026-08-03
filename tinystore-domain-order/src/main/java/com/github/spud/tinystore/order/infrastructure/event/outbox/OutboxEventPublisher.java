package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Outbox 事件发布者（从 Outbox 表读取待发布事件，发送至 Kafka）
 */
@Slf4j
@Service
public class OutboxEventPublisher {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Counter outboxPublishFailureCounter;

    @org.springframework.beans.factory.annotation.Value("${order.kafka.topic.general:tinystore.order.general}")
    private String kafkaTopic;

    public OutboxEventPublisher(MeterRegistry meterRegistry) {
        this.outboxPublishFailureCounter = Counter.builder("tinystore.outbox.publish.failure.total")
            .description("Count of outbox event publish failures")
            .tag("service", "order")
            .register(meterRegistry);
    }

    /**
     * 发布单个事件到 Kafka
     *
     * @param event Outbox 事件
     */
    public void publishEvent(OutboxEventEntity event) {
        try {
            // 构造 Kafka 消息 payload
            KafkaEventMessage message = KafkaEventMessage.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .payload(event.getPayloadJson())
                .traceId(event.getTraceId())
                .occurredAt(event.getCreatedAt().toString())
                .build();

            String messageJson = objectMapper.writeValueAsString(message);

            // 发送至 Kafka
            kafkaTemplate.send(kafkaTopic, event.getAggregateId(), messageJson)
                .thenAccept(result -> {
                    // 发送成功，标记 Outbox 为 PUBLISHED
                    outboxEventService.markAsPublished(event.getEventId());
                    log.info("Outbox event published to Kafka: eventId={}, topic={}, offset={}",
                        event.getEventId(), kafkaTopic, result.getRecordMetadata().offset());
                })
                .exceptionally(ex -> {
                    // 发送失败，增加指标计数并标记为 FAILED
                    outboxPublishFailureCounter.increment();
                    outboxEventService.markAsFailed(event.getEventId(), ex.getMessage());
                    log.error("Failed to publish Outbox event to Kafka: eventId={}, error={}",
                        event.getEventId(), ex.getMessage());
                    return null;
                });

        } catch (Exception e) {
            log.error("Error preparing Outbox event for Kafka: eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
            outboxPublishFailureCounter.increment();
            outboxEventService.markAsFailed(event.getEventId(), e.getMessage());
        }
    }

    /**
     * 批量发布事件：全部异步发送，成功后收集 eventId 一次批量标记 PUBLISHED
     * （替代逐条 thenAccept 标记，消除发布瓶颈）。
     *
     * @param events 待发布事件列表
     */
    public void publishEvents(List<OutboxEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> publishedIds = new CopyOnWriteArrayList<>();
        for (OutboxEventEntity event : events) {
            futures.add(sendEventAsync(event, publishedIds));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> outboxEventService.markAsPublishedBatch(publishedIds));
    }

    /**
     * 异步发送单条事件：成功 → eventId 加入 publishedIds；失败 → 标记 FAILED（不加入）。
     */
    private CompletableFuture<Void> sendEventAsync(OutboxEventEntity event,
                                                   List<String> publishedIds) {
        try {
            KafkaEventMessage message = KafkaEventMessage.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .payload(event.getPayloadJson())
                .traceId(event.getTraceId())
                .occurredAt(event.getCreatedAt().toString())
                .build();
            String messageJson = objectMapper.writeValueAsString(message);
            return kafkaTemplate.send(kafkaTopic, event.getAggregateId(), messageJson)
                .thenAccept(result -> {
                    publishedIds.add(event.getEventId());
                    log.info("Outbox event published to Kafka: eventId={}, topic={}, offset={}",
                        event.getEventId(), kafkaTopic, result.getRecordMetadata().offset());
                })
                .exceptionally(ex -> {
                    outboxPublishFailureCounter.increment();
                    outboxEventService.markAsFailed(event.getEventId(), ex.getMessage());
                    log.error("Failed to publish Outbox event to Kafka: eventId={}, error={}",
                        event.getEventId(), ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            log.error("Error preparing Outbox event for Kafka: eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
            outboxPublishFailureCounter.increment();
            outboxEventService.markAsFailed(event.getEventId(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Kafka 消息 DTO
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class KafkaEventMessage {
        private String eventId;
        private String eventType;
        private String aggregateType;
        private String aggregateId;
        private String payload;
        private String traceId;
        private String occurredAt;
    }
}
