package com.github.spud.tinystore.order.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.infrastructure.kafka.dto.PromotionAckEventDto;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes promotion ack events (PROMOTION_COMMITTED / PROMOTION_COMMIT_FAILED)
 * from tinystore.promotion.general and applies the commit result to the trade.
 * Idempotent via ConsumerEventLogEntity. Manual ack.
 */
@Slf4j
@Component
public class PromotionAckConsumer {

    private static final String CONSUMER_NAME = "order-promotion-ack-consumer";

    private final ObjectMapper objectMapper;
    private final TradeApplicationService tradeApplicationService;
    private final JpaConsumerEventLogRepository consumerEventLogRepository;

    public PromotionAckConsumer(ObjectMapper objectMapper,
                                TradeApplicationService tradeApplicationService,
                                JpaConsumerEventLogRepository consumerEventLogRepository) {
        this.objectMapper = objectMapper;
        this.tradeApplicationService = tradeApplicationService;
        this.consumerEventLogRepository = consumerEventLogRepository;
    }

    @KafkaListener(topics = "${order.kafka.topic.promotion-events:tinystore.promotion.general}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public void handlePromotionAck(String message, Acknowledgment ack) {
        processPromotionAck(message, ack);
    }

    /**
     * Convenience overload for the manual-ack path where the Acknowledgment is
     * not available (e.g. unit tests). Ack is skipped when null.
     */
    public void handlePromotionAck(String message) {
        processPromotionAck(message, null);
    }

    private void processPromotionAck(String message, Acknowledgment ack) {
        PromotionAckEventDto event;
        try {
            event = objectMapper.readValue(message, PromotionAckEventDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid promotion ack event (DLT)", e);
        }
        if (event.getEventId() == null || event.getEventType() == null || event.getTradeId() == null) {
            throw new IllegalArgumentException("Missing required fields in promotion ack event (DLT)");
        }
        if (consumerEventLogRepository.existsByEventIdAndConsumerName(event.getEventId(), CONSUMER_NAME)) {
            log.info("Promotion ack already processed, skip. eventId={}", event.getEventId());
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        try {
            if ("PROMOTION_COMMITTED".equals(event.getEventType())) {
                tradeApplicationService.applyPromotionCommitResult(event.getTradeId(), true, null);
            } else if ("PROMOTION_COMMIT_FAILED".equals(event.getEventType())) {
                tradeApplicationService.applyPromotionCommitResult(event.getTradeId(), false, event.getReason());
            } else {
                log.debug("Ignore promotion ack event type: {}", event.getEventType());
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
            log.error("Failed to process promotion ack, will retry/DLT. eventId={}", event.getEventId(), e);
            throw e;
        }
    }
}
