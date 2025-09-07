package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.infrastructure.domain.order.OrderOutbox;
import com.github.spud.tinystore.infrastructure.domain.order.OrderOutbox.Status;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutBoxRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/8/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventSender {

	private final OrderOutBoxRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	// 最大重试次数
	private static final int MAX_RETRY_COUNT = 5;
	// 每次处理事件数量
	private static final int BATCH_SIZE = 100;
	// 定时任务执行间隔（5秒一次）
	private static final String CRON_EXPRESSION = "*/5 * * * * *";

	/**
	 * 定时扫描并发送Outbox中的事件
	 */
	@Scheduled(cron = CRON_EXPRESSION)
	@Transactional
	public void sendPendingEvents() {
		log.info("Starting to process pending outbox events");

		// 1. 查询待处理事件（加锁防止并发处理）
		List<OrderOutbox> pendingEvents = repository.findPendingEventsWithLock(
			Status.PENDING,
			LocalDateTime.now(),
			org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE)
		);

		if (pendingEvents.isEmpty()) {
			log.info("No pending events to process");
			return;
		}

		log.info("Found {} pending events to process", pendingEvents.size());

		// 2. 逐个发送事件
		for (OrderOutbox event : pendingEvents) {
			processSingleEvent(event);
		}
	}

	/**
	 * 处理单个事件：发送到消息队列并更新状态
	 */
	private void processSingleEvent(OrderOutbox event) {
		try {
			// 标记为处理中
			event.setStatus(OrderOutbox.Status.PROCESSING);
			repository.save(event);

			// 发送到消息队列（主题名可根据事件类型动态生成）
			String topic = "order-events-" + event.getType().name().toLowerCase();
			// TODO: 根据实际情况调整消息发送逻辑
			log.info("Sending event {} (type: {}) to topic {}", event.getId(), event.getType(), topic);
			kafkaTemplate.send(topic, event.getAggregateId().toString(),
				event.getPayloadJson().toString()).get(); // 同步等待结果

			// 发送成功：标记为已发送
			event.setStatus(OrderOutbox.Status.SENT);
			log.info("Event {} (type: {}) sent successfully", event.getId(), event.getType());

		} catch (Exception e) {
			log.error("Failed to send event {}: {}", event.getId(), e.getMessage(), e);
			handleSendFailure(event); // 处理发送失败
		} finally {
			repository.save(event);
		}
	}

	/**
	 * 处理发送失败：重试机制（指数退避）
	 */
	private void handleSendFailure(OrderOutbox event) {
		int newRetryCount = event.getRetryCount() + 1;
		event.setRetryCount(newRetryCount);

		if (newRetryCount >= MAX_RETRY_COUNT) {
			// 达到最大重试次数：标记为失败（人工介入）
			event.setStatus(OrderOutbox.Status.FAILED);
			log.warn("Event {} reached max retry count ({})", event.getId(), MAX_RETRY_COUNT);
		} else {
			// 未达最大次数：设置下次重试时间（指数退避，如1s, 2s, 4s...）
			long delaySeconds = (long) Math.pow(2, newRetryCount);
			event.setNextRetryTime(OffsetDateTime.from(LocalDateTime.now().plusSeconds(delaySeconds)));
			event.setStatus(OrderOutbox.Status.PENDING); // 允许下次重试
			log.info("Event {} will be retried at {} (retry count: {})",
				event.getId(), event.getNextRetryTime(), newRetryCount);
		}
	}
}
