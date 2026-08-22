package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Inventory domain Kafka publisher. Publishes confirm ack events
 * (INVENTORY_CONFIRMED / INVENTORY_CONFIRM_CONFLICT) back to the order domain
 * so the order projection can be driven from the inventory source of truth.
 *
 * <p>Fire-and-forget with async logging; failure does not roll back the confirm —
 * the order domain's {@code InventoryConfirmReconciler} covers the lost-ack case.
 */
@Slf4j
@Component
public class InventoryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${inventory.kafka.topic.general:tinystore.inventory.general}")
    private String topic;

    public InventoryEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishConfirmAck(String eventId, String eventType, String tradeId, String orderId,
                                  String status, String reason) {
        try {
            Map<String, Object> payload = Map.of(
                    "eventId", eventId,
                    "eventType", eventType,
                    "tradeId", tradeId,
                    "orderId", orderId,
                    "status", status,
                    "reason", reason != null ? reason : "");
            String json = objectMapper.writeValueAsString(payload);
            CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
                    kafkaTemplate.send(topic, orderId, json);
            future.whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish inventory confirm ack: eventId={}, error={}",
                            eventId, ex.getMessage(), ex);
                } else {
                    log.info("Published inventory confirm ack: eventId={}, type={}, topic={}",
                            eventId, eventType, topic);
                }
            });
        } catch (Exception e) {
            log.error("Error publishing inventory confirm ack: eventId={}, error={}", eventId, e.getMessage(), e);
        }
    }
}
