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
import org.springframework.transaction.annotation.Propagation;
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

    /** 传输类失败后的初始退避秒数（指数增长）。 */
    @org.springframework.beans.factory.annotation.Value("${order.outbox.retry.backoff-seconds:5}")
    private long retryBackoffSeconds;

    /** 退避上限，避免无限增长的等待。 */
    @org.springframework.beans.factory.annotation.Value("${order.outbox.retry.max-backoff-seconds:60}")
    private long maxRetryBackoffSeconds;

    /**
     * 保存事件到 Outbox
     *
     * @param event 域事件
     * @return 保存成功返回 true
     */
    public boolean saveEvent(OrderDomainEvent event) {
        return doSaveEvent(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveEventInNewTransaction(OrderDomainEvent event) {
        return doSaveEvent(event);
    }

    private boolean doSaveEvent(OrderDomainEvent event) {
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
     * 原子认领一批待发布事件（多副本 claim 协议）。
     *
     * <p>读锁、状态迁移到 PROCESSING、写回 claimed_by/claimed_at 全部发生在**一个事务**内。
     * 这是 C3 的修复要点：原先 {@code FOR UPDATE SKIP LOCKED} 跑在自动提交下，语句一结束锁就没了，
     * 两个副本会取到同一批行并各自发布一次。
     *
     * <p>锁不跨越 Kafka 发送：事务提交后调用方才去发布。
     */
    @Transactional
    public List<OutboxEventEntity> claimPendingEvents(int limit, String instanceId) {
        List<OutboxEventEntity> events = outboxEventJpaRepository.findPendingEvents("PENDING", limit);
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        for (OutboxEventEntity event : events) {
            event.setStatus("PROCESSING");
            event.setClaimedBy(instanceId);
            event.setClaimedAt(now);
        }
        outboxEventJpaRepository.saveAllAndFlush(events);
        log.debug("Claimed {} pending outbox events for instance={}", events.size(), instanceId);
        return events;
    }

    /**
     * 回收僵尸认领：认领后实例崩溃会让事件永远停在 PROCESSING，这里把它们退回 PENDING。
     */
    @Transactional
    public int reclaimStaleClaims(java.time.Duration claimTimeout) {
        int reclaimed = outboxEventJpaRepository.reclaimStaleClaims(
                LocalDateTime.now().minus(claimTimeout));
        if (reclaimed > 0) {
            log.warn("Reclaimed {} stale PROCESSING outbox events (claim timeout {})",
                    reclaimed, claimTimeout);
        }
        return reclaimed;
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
     * 批量标记已发布（一次 UPDATE 替代逐条 SELECT+UPDATE，消除发布瓶颈）。
     *
     * @param eventIds 发布成功的 eventId 列表
     */
    public void markAsPublishedBatch(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        int updated = outboxEventJpaRepository.markAsPublishedBatch(eventIds, LocalDateTime.now());
        log.info("Outbox events marked as published (batch): count={}", updated);
    }

    /**
     * 标记事件发布失败并增加重试计数
     *
     * @param eventId 事件 ID
     * @param error 错误信息
     */
    public void markAsFailed(String eventId, String error) {
        markAsFailed(eventId, error, null);
    }

    /**
     * 标记事件发布失败并决定后续走向。
     *
     * <p>关键区分（C12）：
     * <ul>
     *   <li><b>不可恢复</b>（JSON 序列化 / 参数非法）：直接 FAILED，重试没有意义；</li>
     *   <li><b>传输类失败</b>（Kafka 不可用、超时）：退回 PENDING 并设置 {@code next_attempt_at}
     *       做指数退避，<b>不</b>因为几次 broker 连接失败就永久放弃事件——
     *       否则一次几分钟的 Kafka 抖动就会让关键事件（如 INVENTORY_RESERVE_DB）永久丢失。</li>
     * </ul>
     */
    public void markAsFailed(String eventId, String error, Throwable cause) {
        outboxEventJpaRepository.findByEventId(eventId).ifPresent(event -> {
            event.setRetryCount((event.getRetryCount() != null ? event.getRetryCount() : 0) + 1);
            event.setLastError(error);
            // 无论走哪条分支都释放认领，否则该行不会被重新认领。
            event.setClaimedBy(null);
            event.setClaimedAt(null);
            if (isUnrecoverable(cause)) {
                event.setStatus("FAILED");
                event.setNextAttemptAt(null);
            } else {
                event.setStatus("PENDING");
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(event.getRetryCount())));
            }
            outboxEventJpaRepository.save(event);
            log.warn("Outbox event marked as failed: eventId={}, retryCount={}, error={}",
                eventId, event.getRetryCount(), error);
        });
    }

    /** 载荷/校验类失败重试无益，直接终态；其余（传输类）都允许继续退避重试。 */
    private boolean isUnrecoverable(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof com.fasterxml.jackson.core.JsonProcessingException
                    || current instanceof IllegalArgumentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long backoffSeconds(int retryCount) {
        long exponent = Math.min(Math.max(retryCount - 1, 0), 6);
        long computed = retryBackoffSeconds * (1L << exponent);
        return Math.min(computed, maxRetryBackoffSeconds);
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
