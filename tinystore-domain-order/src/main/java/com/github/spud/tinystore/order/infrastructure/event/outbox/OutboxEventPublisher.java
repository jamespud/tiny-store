package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @org.springframework.beans.factory.annotation.Value("${order.kafka.topic.general:tinystore.order.general}")
    private String kafkaTopic;

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
                    // 发送失败，标记为 FAILED 并增加重试计数
                    outboxEventService.markAsFailed(event.getEventId(), ex.getMessage());
                    log.error("Failed to publish Outbox event to Kafka: eventId={}, error={}",
                        event.getEventId(), ex.getMessage());
                    return null;
                });

        } catch (Exception e) {
            log.error("Error preparing Outbox event for Kafka: eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
            outboxEventService.markAsFailed(event.getEventId(), e.getMessage());
        }
    }

    /**
     * 批量发布事件
     *
     * @param events 待发布事件列表
     */
    public void publishEvents(List<OutboxEventEntity> events) {
        for (OutboxEventEntity event : events) {
            publishEvent(event);
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
