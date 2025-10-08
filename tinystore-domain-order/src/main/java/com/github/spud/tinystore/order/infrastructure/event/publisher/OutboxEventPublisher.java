package com.github.spud.tinystore.order.infrastructure.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Outbox 事件发布器
 * 定时扫描 Outbox 表，将事件发布到 Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

	private final OutboxEventService outboxEventService;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	// 发布配置
	private static final int BATCH_SIZE = 100;
	private static final int MAX_RETRIES = 3;
	private static final String TOPIC_PREFIX = "tinystore.order.";

	/**
	 * 定时发布 Outbox 事件到 Kafka
	 * 每10秒执行一次
	 */
	@Scheduled(fixedDelay = 10000, initialDelay = 5000)
	public void publishPendingEvents() {
		try {
			List<OrderOutboxEventPO> pendingEvents =
				outboxEventService.findPendingEvents(BATCH_SIZE);

			if (pendingEvents.isEmpty()) {
				return;
			}

			log.info("开始发布 Outbox 事件到 Kafka: eventCount={}", pendingEvents.size());

			List<CompletableFuture<Void>> futures = pendingEvents.stream()
				.map(this::publishEventAsync)
				.collect(Collectors.toList());

			// 等待所有事件发布完成
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.whenComplete((result, throwable) -> {
					if (throwable != null) {
						log.error("批量发布 Outbox 事件部分失败", throwable);
					} else {
						log.info("批量发布 Outbox 事件完成: eventCount={}", pendingEvents.size());
					}
				});

		} catch (Exception e) {
			log.error("发布 Outbox 事件异常", e);
		}
	}

	/**
	 * 定时处理失败的事件重试
	 * 每分钟执行一次
	 */
	@Scheduled(fixedDelay = 60000, initialDelay = 30000)
	public void retryFailedEvents() {
		try {
			List<OrderOutboxEventPO> retryableEvents =
				outboxEventService.findRetryableEvents(MAX_RETRIES);

			if (retryableEvents.isEmpty()) {
				return;
			}

			log.info("开始重试失败的 Outbox 事件: eventCount={}", retryableEvents.size());

			retryableEvents.forEach(this::publishEventAsync);

		} catch (Exception e) {
			log.error("重试失败事件异常", e);
		}
	}

	/**
	 * 异步发布单个事件
	 */
	private CompletableFuture<Void> publishEventAsync(OrderOutboxEventPO event) {
		try {
			String topic = getTopicName(event.getEventType());
			String key = event.getOrderId(); // 使用订单号作为分区键

			// 构造 Kafka 消息
			KafkaEventMessage kafkaMessage = new KafkaEventMessage(
				event.getId().toString(),
				event.getOrderNo(),
				event.getEventType(),
				event.getEventPayload(),
				event.getTraceId(),
				event.getCreatedAt().toString()
			);

			String messageJson = objectMapper.writeValueAsString(kafkaMessage);

			// 发送到 Kafka
			CompletableFuture<SendResult<String, String>> future =
				kafkaTemplate.send(topic, key, messageJson);

			return future.handle((result, throwable) -> {
				if (throwable != null) {
					log.error("发布事件到 Kafka 失败: eventId={}, orderNo={}, topic={}",
						event.getId(), event.getOrderNo(), topic, throwable);

					// 标记事件失败
					outboxEventService.markEventFailed(event.getId());
				} else {
					log.debug("发布事件到 Kafka 成功: eventId={}, orderNo={}, topic={}, partition={}, offset={}",
						event.getId(), event.getOrderNo(), topic,
						result.getRecordMetadata().partition(),
						result.getRecordMetadata().offset());

					// 标记事件已发送
					outboxEventService.markEventsSent(List.of(event.getId()));
				}
				return null;
			});

		} catch (JsonProcessingException e) {
			log.error("序列化 Kafka 消息失败: eventId={}", event.getId(), e);
			outboxEventService.markEventFailed(event.getId());
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * 根据事件类型获取 Kafka Topic 名称
	 */
	private String getTopicName(String eventType) {
		// 事件类型到 Topic 的映射规则
		switch (eventType) {
			case "OrderPaidEvent":
				return TOPIC_PREFIX + "paid";
			case "OrderStatusChangedEvent":
				return TOPIC_PREFIX + "status-changed";
			case "OrderShippedEvent":
				return TOPIC_PREFIX + "shipped";
			case "OrderCompletedEvent":
				return TOPIC_PREFIX + "completed";
			case "OrderCancelledEvent":
				return TOPIC_PREFIX + "cancelled";
			default:
				return TOPIC_PREFIX + "general";
		}
	}

	/**
	 * 定时清理已完成的事件
	 * 每天凌晨2点执行
	 */
	@Scheduled(cron = "0 0 2 * * ?")
	public void cleanCompletedEvents() {
		try {
			outboxEventService.cleanCompletedEvents(7); // 保留7天
			log.info("清理过期的 Outbox 事件完成");
		} catch (Exception e) {
			log.error("清理过期事件异常", e);
		}
	}

	/**
	 * Kafka 事件消息格式
	 */
	public static class KafkaEventMessage {
		private String eventId;
		private String orderNo;
		private String eventType;
		private String payload;
		private String traceId;
		private String timestamp;

		public KafkaEventMessage() {
		}

		public KafkaEventMessage(String eventId, String orderNo, String eventType,
		                         String payload, String traceId, String timestamp) {
			this.eventId = eventId;
			this.orderNo = orderNo;
			this.eventType = eventType;
			this.payload = payload;
			this.traceId = traceId;
			this.timestamp = timestamp;
		}

		// Getters and Setters
		public String getEventId() {
			return eventId;
		}

		public void setEventId(String eventId) {
			this.eventId = eventId;
		}

		public String getOrderNo() {
			return orderNo;
		}

		public void setOrderNo(String orderNo) {
			this.orderNo = orderNo;
		}

		public String getEventType() {
			return eventType;
		}

		public void setEventType(String eventType) {
			this.eventType = eventType;
		}

		public String getPayload() {
			return payload;
		}

		public void setPayload(String payload) {
			this.payload = payload;
		}

		public String getTraceId() {
			return traceId;
		}

		public void setTraceId(String traceId) {
			this.traceId = traceId;
		}

		public String getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(String timestamp) {
			this.timestamp = timestamp;
		}
	}
}