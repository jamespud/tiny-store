package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Outbox 事件清理定时任务
 *
 * <p>职责：
 * - 定期清理已成功发布且超过保留期的 Outbox 事件
 * - 避免 outbox_event 表无限增长
 * - 保留最近的事件用于故障排查和审计
 *
 * <p>执行策略：
 * - 默认每天凌晨 3 点执行
 * - 删除 status = PUBLISHED 且 published_at < (NOW - retention_days) 的事件
 * - 批量删除以提升性能
 */
@Slf4j
@Component
public class OutboxCleanupScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Value("${order.outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${order.outbox.cleanup.enabled:true}")
    private boolean enabled;

    public OutboxCleanupScheduler(OutboxEventJpaRepository outboxEventJpaRepository) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
    }

    /**
     * 清理已发布的 Outbox 事件
     *
     * <p>执行时间：每天凌晨 3 点
     * <p>清理策略：删除 PUBLISHED 状态且超过保留期的事件
     */
    @Scheduled(cron = "${order.outbox.cleanup.cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupPublishedEvents() {
        if (!enabled) {
            log.debug("Outbox cleanup is disabled, skipping");
            return;
        }

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            log.info("Starting outbox cleanup: retention_days={}, cutoff_published_at={}",
                    retentionDays, cutoffTime);

            int deletedCount = outboxEventJpaRepository.deletePublishedEventsBefore(cutoffTime);

            log.info("Outbox cleanup completed: deleted_count={}, retention_days={}",
                    deletedCount, retentionDays);

        } catch (Exception e) {
            log.error("Outbox cleanup failed", e);
            // 不抛出异常，避免影响后续调度
        }
    }
}
