package com.github.spud.tinystore.order.domain.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.spud.tinystore.order.domain.model.Outbox;
import com.github.spud.tinystore.order.domain.repository.OutboxRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Outbox 事件发布调度器
 *
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@Service
public class OutboxEventPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_BASE_DELAY_MINUTES = 1;

    private final OutboxRepository outboxRepository;
    private final EventPublishingService eventPublishingService;

    public OutboxEventPublisher(OutboxRepository outboxRepository, EventPublishingService eventPublishingService) {
        this.outboxRepository = outboxRepository;
        this.eventPublishingService = eventPublishingService;
    }

    /**
     * 定期发布待发布的事件
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000) // 30秒
    @Async
    public void publishPendingEvents() {
        log.debug("Starting to publish pending outbox events");

        List<Outbox> pendingEvents = outboxRepository.findPendingEvents(BATCH_SIZE);

        for (Outbox event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish event: eventId={}, eventType={}",
                        event.getEventId(), event.getEventType(), e);
                handlePublishFailure(event, e.getMessage());
            }
        }

        log.debug("Finished publishing pending outbox events, processed: {}", pendingEvents.size());
    }

    /**
     * 定期重试失败的事件
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    @Async
    public void retryFailedEvents() {
        log.debug("Starting to retry failed outbox events");

        Instant now = Instant.now();
        List<Outbox> retryEvents = outboxRepository.findEventsForRetry(now, BATCH_SIZE);

        for (Outbox event : retryEvents) {
            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("Event exceeded max retry count, giving up: eventId={}, eventType={}, retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount());
                continue;
            }

            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Failed to retry event: eventId={}, eventType={}, retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount(), e);
                handlePublishFailure(event, e.getMessage());
            }
        }

        log.debug("Finished retrying failed outbox events, processed: {}", retryEvents.size());
    }

    /**
     * 清理已发布的老事件
     * 每天执行一次，删除7天前的已发布事件
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    @Async
    public void cleanupOldEvents() {
        log.info("Starting cleanup of old published outbox events");

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        int deletedCount = outboxRepository.deletePublishedEventsBefore(sevenDaysAgo);

        log.info("Cleanup completed, deleted {} old published events", deletedCount);
    }

    /**
     * 发布单个事件
     */
    private void publishEvent(Outbox event) {
        log.debug("Publishing event: eventId={}, eventType={}", event.getEventId(), event.getEventType());

        try {
            // 调用事件发布服务（例如发送到消息队列）
            eventPublishingService.publish(event);

            // 标记为已发布
            outboxRepository.markAsPublished(event.getEventId());

            log.debug("Event published successfully: eventId={}, eventType={}",
                    event.getEventId(), event.getEventType());
        } catch (Exception e) {
            // 重新抛出异常，让上层处理
            throw new RuntimeException("Failed to publish event: " + event.getEventId(), e);
        }
    }

    /**
     * 处理发布失败
     */
    private void handlePublishFailure(Outbox event, String errorMessage) {
        int newRetryCount = event.getRetryCount() + 1;

        // 计算下次重试时间（指数退避）
        long delayMinutes = RETRY_BASE_DELAY_MINUTES * (long) Math.pow(2, newRetryCount - 1);
        Instant nextRetryAt = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);

        // 更新状态
        outboxRepository.updateStatus(
                event.getEventId(),
                Outbox.PublishStatus.FAILED,
                newRetryCount,
                nextRetryAt,
                errorMessage
        );

        log.warn("Event publish failed, scheduled for retry: eventId={}, retryCount={}, nextRetryAt={}",
                event.getEventId(), newRetryCount, nextRetryAt);
    }
}