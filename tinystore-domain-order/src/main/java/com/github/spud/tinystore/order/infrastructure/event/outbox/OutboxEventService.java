package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 事件服务
 */
@Slf4j
@Service
public class OutboxEventService {

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 保存事件到 Outbox
     *
     * @param event 域事件
     * @return 保存成功返回 true
     */
    public boolean saveEvent(OrderDomainEvent event) {
        try {
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }
            if (event.getOccurredAt() == null) {
                event.setOccurredAt(LocalDateTime.now());
            }

            String payloadJson = event.getPayloadJson() != null ? event.getPayloadJson() : "{}";

            OutboxEventEntity entity = OutboxEventEntity.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType().getCode())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .payloadJson(payloadJson)
                .status("PENDING")
                .traceId(event.getTraceId())
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();

            outboxEventJpaRepository.save(entity);
            log.info("Outbox event saved: eventId={}, eventType={}, aggregateId={}",
                entity.getEventId(), entity.getEventType(), entity.getAggregateId());
            return true;
        } catch (Exception e) {
            log.error("Failed to save Outbox event", e);
            return false;
        }
    }

    /**
     * 获取待发布的事件列表
     *
     * @param limit 限制数量
     * @return 事件列表
     */
    public List<OutboxEventEntity> getPendingEvents(int limit) {
        return outboxEventJpaRepository.findPendingEvents("PENDING", limit);
    }

    /**
     * 标记事件为已发布
     *
     * @param eventId 事件 ID
     */
    public void markAsPublished(String eventId) {
        outboxEventJpaRepository.findByEventId(eventId).ifPresent(event -> {
            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            outboxEventJpaRepository.save(event);
            log.info("Outbox event marked as published: eventId={}", eventId);
        });
    }

    /**
     * 标记事件发布失败并增加重试计数
     *
     * @param eventId 事件 ID
     * @param error 错误信息
     */
    public void markAsFailed(String eventId, String error) {
        outboxEventJpaRepository.findByEventId(eventId).ifPresent(event -> {
            event.setRetryCount((event.getRetryCount() != null ? event.getRetryCount() : 0) + 1);
            event.setLastError(error);
            // 重试超过 3 次标记为 FAILED
            if (event.getRetryCount() >= 3) {
                event.setStatus("FAILED");
            }
            outboxEventJpaRepository.save(event);
            log.warn("Outbox event marked as failed: eventId={}, retryCount={}, error={}",
                eventId, event.getRetryCount(), error);
        });
    }

    /**
     * 查询失败的 Outbox 事件
     *
     * @param limit 限制数量
     * @param since 起始时间（查询此时间之后创建的事件）
     * @param eventType 事件类型过滤（可选）
     * @return 失败事件列表
     */
    public List<OutboxEventEntity> findFailedEvents(int limit, LocalDateTime since, String eventType) {
        Pageable pageable = PageRequest.of(0, limit);

        if (eventType != null && !eventType.isBlank()) {
            return outboxEventJpaRepository.findByStatusAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                "FAILED", eventType, since, pageable);
        } else {
            return outboxEventJpaRepository.findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                "FAILED", since, pageable);
        }
    }

    /**
     * 重试失败的 Outbox 事件（重置状态为 PENDING）
     *
     * @param eventId 事件 ID
     * @throws IllegalStateException 当事件不存在时
     */
    @Transactional
    public void retryEvent(String eventId) {
        OutboxEventEntity event = outboxEventJpaRepository.findByEventId(eventId)
            .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));

        // 如果已经是 PUBLISHED 状态，跳过重试
        if ("PUBLISHED".equals(event.getStatus())) {
            log.info("Outbox event already published, skip retry: eventId={}", eventId);
            return;
        }

        // 重置状态为 PENDING，清零重试计数
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setLastError(null);
        outboxEventJpaRepository.save(event);

        log.info("Outbox event reset to PENDING for retry: eventId={}, previousStatus={}",
            eventId, event.getStatus());
    }
}
