package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.domain.command.InventoryAdjustCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.enums.AdjustmentReason;
import com.github.spud.tinystore.inventory.domain.service.InventoryAdjustmentDomainService;
import com.github.spud.tinystore.inventory.domain.service.InventoryReservationDomainService;
import com.github.spud.tinystore.inventory.domain.value.AdjustmentResult;
import com.github.spud.tinystore.inventory.domain.value.ReservationResult;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.infrastructure.kafka.dto.OrderDomainEventDto;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory domain Kafka consumer. Subscribes to tinystore.order.general,
 * filters by INVENTORY_* event types, dispatches to domain services.
 * Idempotent via ConsumerEventLogEntity. DLT for persistent failures.
 */
@Slf4j
@Component
public class OrderEventConsumer {

    private static final String CONSUMER_NAME = "inventory-service";

    private final InventoryReservationDomainService reservationDomainService;
    private final InventoryAdjustmentDomainService adjustmentDomainService;
    private final JpaConsumerEventLogRepository consumerEventLogRepository;
    private final InventoryEventPublisher inventoryEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(InventoryReservationDomainService reservationDomainService,
                               InventoryAdjustmentDomainService adjustmentDomainService,
                               JpaConsumerEventLogRepository consumerEventLogRepository,
                               InventoryEventPublisher inventoryEventPublisher,
                               ObjectMapper objectMapper) {
        this.reservationDomainService = reservationDomainService;
        this.adjustmentDomainService = adjustmentDomainService;
        this.consumerEventLogRepository = consumerEventLogRepository;
        this.inventoryEventPublisher = inventoryEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${inventory.kafka.topic.order-events:tinystore.order.general}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderEvent(String message, Acknowledgment ack) {
        log.debug("Received order event: {}", message);

        OrderDomainEventDto event;
        try {
            event = objectMapper.readValue(message, OrderDomainEventDto.class);
        } catch (Exception e) {
            log.error("Failed to parse order event (poison, DLT): message={}", message, e);
            throw new IllegalArgumentException("Invalid JSON for order event", e);
        }

        String eventId = event.getEventId();
        String eventType = event.getEventType();
        String payload = event.getPayload();

        if (eventId == null || eventType == null) {
            throw new IllegalArgumentException("Missing eventId or eventType");
        }

        if (consumerEventLogRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Event already processed (idempotent): eventId={}, eventType={}", eventId, eventType);
            ack.acknowledge();
            return;
        }

        try {
            switch (eventType) {
                case "INVENTORY_RESERVE_DB" -> handleInventoryReserveDb(eventId, payload);
                case "INVENTORY_CONFIRM" -> handleInventoryConfirm(eventId, payload);
                case "INVENTORY_RELEASE" -> handleInventoryRelease(eventId, payload);
                case "INVENTORY_ADJUST" -> handleInventoryAdjust(eventId, payload);
                default -> log.debug("Ignore event type: {}", eventType);
            }
            markProcessed(eventId, null);
        } catch (Exception e) {
            log.error("Failed to process event: eventId={}, eventType={}", eventId, eventType, e);
            throw new RuntimeException("Event processing failed: " + eventType, e);
        }

        ack.acknowledge();
        log.info("Event processed: eventId={}, eventType={}", eventId, eventType);
    }

    @SuppressWarnings("unchecked")
    private void handleInventoryReserveDb(String eventId, String payload) throws Exception {
        Map<String, Object> p = objectMapper.readValue(payload, Map.class);
        reservationDomainService.saveReservation(
                (String) p.get("reservationId"),
                (String) p.get("shopId"),
                (String) p.get("skuId"),
                ((Number) p.get("quantity")).intValue(),
                (String) p.get("tradeId"),
                (String) p.get("orderId"),
                eventId,
                OffsetDateTime.parse((String) p.get("expireAt")));
    }

    @SuppressWarnings("unchecked")
    private void handleInventoryConfirm(String eventId, String payload) throws Exception {
        Map<String, Object> p = objectMapper.readValue(payload, Map.class);
        List<Map<String, Object>> pairs = (List<Map<String, Object>>) p.get("occupyPairs");
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(eventId)
                .paymentId((String) p.get("paymentId"))
                .tradeId((String) p.get("tradeId"))
                .orderId((String) p.get("orderId"))
                .traceId(eventId)
                .occupyPairs(pairs.stream()
                        .map(pair -> OccupyPair.builder()
                                .shopId((String) pair.get("shopId"))
                                .skuId((String) pair.get("skuId"))
                                .occupyId((String) pair.get("occupyId"))
                                .build())
                        .toList())
                .build();
        ReservationResult result = reservationDomainService.confirm(command);
        if (result.isSuccess()) {
            inventoryEventPublisher.publishConfirmAck(
                    UUID.randomUUID().toString(), "INVENTORY_CONFIRMED",
                    command.getTradeId(), command.getOrderId(),
                    result.getResultingStatus().getCode(), null);
        } else {
            inventoryEventPublisher.publishConfirmAck(
                    UUID.randomUUID().toString(), "INVENTORY_CONFIRM_CONFLICT",
                    command.getTradeId(), command.getOrderId(),
                    result.getConflictReservationIds().isEmpty() ? "CONFLICT" : "RESERVATION_CONFLICT",
                    result.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInventoryRelease(String eventId, String payload) throws Exception {
        Map<String, Object> p = objectMapper.readValue(payload, Map.class);
        List<Map<String, Object>> pairs = (List<Map<String, Object>>) p.get("occupyPairs");
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(eventId)
                .orderId((String) p.get("orderId"))
                .reason((String) p.getOrDefault("reason", "ASYNC_RELEASE"))
                .occupyPairs(pairs.stream()
                        .map(pair -> OccupyPair.builder()
                                .shopId((String) pair.get("shopId"))
                                .skuId((String) pair.get("skuId"))
                                .occupyId((String) pair.get("occupyId"))
                                .build())
                        .toList())
                .build();
        reservationDomainService.release(command);
    }

    @SuppressWarnings("unchecked")
    private void handleInventoryAdjust(String eventId, String payload) throws Exception {
        Map<String, Object> p = objectMapper.readValue(payload, Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) p.get("items");
        InventoryAdjustCommand command = InventoryAdjustCommand.builder()
                .idempotencyKey(eventId)
                .reason(AdjustmentReason.fromCode((String) p.get("reason")))
                .referenceId((String) p.get("refundId"))
                .tradeId((String) p.get("tradeId"))
                .items(items.stream()
                        .map(item -> InventoryAdjustCommand.Item.builder()
                                .shopId((String) item.get("shopId"))
                                .skuId((String) item.get("skuId"))
                                .delta(((Number) item.get("delta")).longValue())
                                .build())
                        .toList())
                .build();
        AdjustmentResult result = adjustmentDomainService.adjust(command);
        adjustmentDomainService.compensateRedisAfterCommit(result);
    }

    private void markProcessed(String eventId, String errorMessage) {
        ConsumerEventLogEntity logEntry = new ConsumerEventLogEntity(
                UUID.randomUUID(), eventId, CONSUMER_NAME,
                errorMessage != null ? "FAILED" : "PROCESSED",
                LocalDateTime.now(), errorMessage, LocalDateTime.now());
        consumerEventLogRepository.save(logEntry);
    }
}
