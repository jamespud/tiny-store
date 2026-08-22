package com.github.spud.tinystore.order.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.infrastructure.kafka.dto.InventoryConfirmAckEventDto;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumes inventory domain confirm ack events (INVENTORY_CONFIRMED /
 * INVENTORY_CONFIRM_CONFLICT) from tinystore.inventory.general and drives the
 * order-side shopOrder inventory projection from the inventory source of truth.
 * Idempotent via ConsumerEventLogEntity. Manual ack.
 */
@Slf4j
@Component
public class InventoryConfirmAckConsumer {

    private static final String CONSUMER_NAME = "order-inventory-confirm-ack-consumer";

    private final ObjectMapper objectMapper;
    private final TradeApplicationService tradeApplicationService;
    private final JpaConsumerEventLogRepository consumerEventLogRepository;

    public InventoryConfirmAckConsumer(ObjectMapper objectMapper,
                                       TradeApplicationService tradeApplicationService,
                                       JpaConsumerEventLogRepository consumerEventLogRepository) {
        this.objectMapper = objectMapper;
        this.tradeApplicationService = tradeApplicationService;
        this.consumerEventLogRepository = consumerEventLogRepository;
    }

    @KafkaListener(topics = "${order.kafka.topic.inventory-events:tinystore.inventory.general}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public void handleInventoryConfirmAck(String message, Acknowledgment ack) {
        process(message, ack);
    }

    /**
     * Convenience overload for the manual-ack-free path (unit tests).
     */
    public void handleInventoryConfirmAck(String message) {
        process(message, null);
    }

    private void process(String message, Acknowledgment ack) {
        InventoryConfirmAckEventDto event;
        try {
            event = objectMapper.readValue(message, InventoryConfirmAckEventDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid inventory confirm ack event (DLT)", e);
        }
        if (event.getEventId() == null || event.getEventType() == null || event.getOrderId() == null) {
            throw new IllegalArgumentException("Missing required fields in inventory confirm ack (DLT)");
        }
        if (consumerEventLogRepository.existsByEventIdAndConsumerName(event.getEventId(), CONSUMER_NAME)) {
            log.info("Inventory confirm ack already processed, skip. eventId={}", event.getEventId());
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }
        try {
            if ("INVENTORY_CONFIRMED".equals(event.getEventType())) {
                tradeApplicationService.markShopOrderInventoryConfirmed(event.getOrderId());
            } else if ("INVENTORY_CONFIRM_CONFLICT".equals(event.getEventType())) {
                tradeApplicationService.markShopOrderInventoryConflict(event.getOrderId());
            } else {
                log.debug("Ignore inventory confirm ack type: {}", event.getEventType());
            }
            ConsumerEventLogEntity logEntity = new ConsumerEventLogEntity();
            logEntity.setId(UUID.randomUUID());
            logEntity.setEventId(event.getEventId());
            logEntity.setConsumerName(CONSUMER_NAME);
            logEntity.setProcessedAt(LocalDateTime.now());
            consumerEventLogRepository.save(logEntity);
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to process inventory confirm ack, will retry/DLT. eventId={}", event.getEventId(), e);
            throw e;
        }
    }
}
