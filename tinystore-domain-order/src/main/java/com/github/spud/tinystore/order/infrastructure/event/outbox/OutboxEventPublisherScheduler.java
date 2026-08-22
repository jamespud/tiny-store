package com.github.spud.tinystore.order.infrastructure.event.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox 事件发布调度器（定时轮询 Outbox 表并发布至 Kafka）
 *
 * <p><b>已知风险：并发多实例无锁机制</b>
 * <p>当前实现未使用分布式锁或行级锁（SELECT FOR UPDATE SKIP LOCKED），
 * 多实例部署时可能导致同一条 PENDING 事件被多个实例同时取到并重复发送到 Kafka。
 *
 * <p><b>缓解措施：</b>
 * <ul>
 *   <li>消费者侧必须做好幂等（当前 inventory/promotion 已实现幂等）</li>
 *   <li>Kafka topic partition 策略可部分降低冲突概率</li>
 * </ul>
 *
 * <p><b>推荐后续优化方案（Phase3+）：</b>
 * <ul>
 *   <li>方案1：使用 SELECT FOR UPDATE SKIP LOCKED（需要支持该语法的数据库版本，如 PostgreSQL 9.5+）</li>
 *   <li>方案2：引入 PROCESSING 状态 + claimedBy/claimedAt 字段，定期清理僵尸 claim</li>
 *   <li>方案3：集成 ShedLock 库（基于数据库/Redis 的分布式锁）</li>
 * </ul>
 */
@Slf4j
@Service
@EnableScheduling
public class OutboxEventPublisherScheduler {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Value("${order.outbox.poll-interval:5000}")
    private long pollInterval;

    @Value("${order.outbox.batch-size:1000}")
    private int batchSize;

    /**
     * 定时轮询 Outbox 待发布事件
     * 默认每 5 秒扫描一次，每批最多 1000 条记录
     */
    @Scheduled(fixedDelayString = "${order.outbox.poll-interval:5000}", scheduler = "outboxTaskScheduler")
    public void pollAndPublishPendingEvents() {
        try {
            List pendingEvents = outboxEventService.getPendingEvents(batchSize);
            
            if (pendingEvents == null || pendingEvents.isEmpty()) {
                log.debug("No pending Outbox events to publish");
                return;
            }

            log.info("Found {} pending Outbox events, publishing to Kafka...", pendingEvents.size());
            outboxEventPublisher.publishEvents(pendingEvents);

        } catch (Exception e) {
            log.error("Error in Outbox event polling scheduler", e);
        }
    }
}
