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

    @Value("${order.outbox.batch-size:100}")
    private int batchSize;

    /**
     * 定时轮询 Outbox 待发布事件
     * 默认每 5 秒扫描一次，每批最多 100 条记录
     */
    @Scheduled(fixedDelayString = "${order.outbox.poll-interval:5000}")
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
