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
        // One token per claim call: the events were claimed atomically together, so they share a lease.
        String claimToken = java.util.UUID.randomUUID().toString();
        for (OutboxEventEntity event : events) {
            event.setStatus("PROCESSING");
            event.setClaimedBy(instanceId);
            event.setClaimedAt(now);
            event.setClaimToken(claimToken);
        }
        outboxEventJpaRepository.saveAllAndFlush(events);
        log.debug("Claimed {} pending outbox events for instance={} token={}", events.size(), instanceId,
                claimToken);
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
     * <p>Unfenced admin/test path: this method does not hold a lease, so it may only complete rows that are
     * not under one (see {@link OutboxEventJpaRepository#markAsPublishedUnfenced}). The publisher itself
     * always claims first and goes through {@link #markAsPublishedBatch(List, String)}.
     *
     * @param eventId 事件 ID
     */
    public void markAsPublished(String eventId) {
        int updated = outboxEventJpaRepository.markAsPublishedUnfenced(eventId, LocalDateTime.now());
        if (updated == 0) {
            log.warn("Outbox publish completion (unfenced) affected 0 rows: eventId={} is either already "
                    + "PUBLISHED or currently held under another worker's lease", eventId);
            return;
        }
        log.info("Outbox event marked as published: eventId={}", eventId);
    }

    /**
     * 批量标记已发布（一次 UPDATE 替代逐条 SELECT+UPDATE）。
     *
     * <p>P1-2 / round-3 P1：这条 UPDATE 严格带 claim_token 围栏 —— 只影响**本次认领仍然有效**的行。若这批
     * 事件在发布期间被判定为僵尸认领并回收（owner 卡顿超过 claimTimeout），影响行数会小于入参数量，旧
     * worker 不得把它们写成 PUBLISHED，也不得在新 owner 已经 PUBLISHED 之后把它们打回 PENDING。
     *
     * @param eventIds 发布成功的 eventId 列表
     * @param claimToken 本次认领的 fencing token
     */
    public void markAsPublishedBatch(List<String> eventIds, String claimToken) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        if (claimToken == null) {
            // The caller did not claim these rows (admin/direct invocation): use the unfenced completion,
            // which still refuses to touch a live lease.
            for (String eventId : eventIds) {
                outboxEventJpaRepository.markAsPublishedUnfenced(eventId, LocalDateTime.now());
            }
            log.info("Outbox events marked as published (batch, unfenced path): count={}", eventIds.size());
            return;
        }
        int updated = outboxEventJpaRepository.markAsPublishedBatch(eventIds, LocalDateTime.now(), claimToken);
        if (updated != eventIds.size()) {
            log.warn("Outbox publish completion was partially fenced: requested={}, updated={}, token={} -- "
                    + "the remaining events were reclaimed by another worker and are no longer ours",
                    eventIds.size(), updated, claimToken);
        }
        log.info("Outbox events marked as published (batch): count={}", updated);
    }

    /**
     * 标记事件发布失败并增加重试计数
     *
     * @param eventId 事件 ID
     * @param error 错误信息
     */
    public void markAsFailed(String eventId, String error) {
        // Admin/cleanup caller: it does not hold a lease, so use the unfenced path (which still refuses to
        // clobber a live lease or re-open a PUBLISHED event).
        boolean unrecoverable = isUnrecoverable(null);
        int retryCount = outboxEventJpaRepository.findByEventId(eventId)
                .map(event -> event.getRetryCount() != null ? event.getRetryCount() : 0)
                .orElse(0);
        int nextRetryCount = retryCount + 1;
        LocalDateTime nextAttemptAt = unrecoverable ? null
                : LocalDateTime.now().plusSeconds(backoffSeconds(nextRetryCount));
        int updated = outboxEventJpaRepository.markAsFailedUnfenced(eventId, error,
                unrecoverable ? "FAILED" : "PENDING", nextAttemptAt);
        if (updated == 0) {
            log.warn("Outbox failure update (unfenced) affected 0 rows: eventId={}", eventId);
            return;
        }
        log.warn("Outbox event marked as failed (unfenced path): eventId={}, status={}, error={}",
                eventId, unrecoverable ? "FAILED" : "PENDING", error);
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
    public void markAsFailed(String eventId, String claimToken, String error, Throwable cause) {
        // P1-2: fenced update. Once the lease is reclaimed the previous owner must not be able to release or
        // re-pend the row the new owner is working on (that is what let a stalled worker override a live
        // lease), and round-3 P1: it must also not be able to re-pend a row the new owner already PUBLISHED.
        // Zero affected rows means "you no longer hold this event" -- nothing to do.
        if (claimToken == null) {
            markAsFailed(eventId, error);
            return;
        }
        boolean unrecoverable = isUnrecoverable(cause);
        int retryCount = outboxEventJpaRepository.findByEventId(eventId)
                .map(event -> event.getRetryCount() != null ? event.getRetryCount() : 0)
                .orElse(0);
        int nextRetryCount = retryCount + 1;
        LocalDateTime nextAttemptAt = unrecoverable ? null
                : LocalDateTime.now().plusSeconds(backoffSeconds(nextRetryCount));

        int updated = outboxEventJpaRepository.markAsFailedIfOwned(eventId, claimToken, error,
                unrecoverable ? "FAILED" : "PENDING", nextAttemptAt);
        if (updated == 0) {
            log.warn("Outbox failure update ignored (claim already reclaimed by another worker): eventId={}, "
                    + "token={}", eventId, claimToken);
            return;
        }
        log.warn("Outbox event marked as failed: eventId={}, retryCount={}, status={}, error={}",
                eventId, nextRetryCount, unrecoverable ? "FAILED" : "PENDING", error);
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
