package com.github.spud.tinystore.promotion.infrastructure.kafka;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.application.service.PromotionOrderEventHandler;
import com.github.spud.tinystore.promotion.infrastructure.kafka.dto.OrderDomainEventDto;

/**
 * 订单事件消费者
 * 订阅 tinystore.order.general，消费订单域的事件并同步促销状态
 * <p>
 * 错误处理策略（DLT）：
 * - 毒消息（JSON 解析/字段缺失）：抛出不可重试异常 → 立即进 DLT
 * - 业务异常：抛出 → DefaultErrorHandler 重试 → 最终 DLT
 * - 成功：手动 ack
 *
 * @author Spud
 * @date 2026/02/17
 */
@Component
public class OrderEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

	private final PromotionOrderEventHandler handler;
	private final ObjectMapper objectMapper;

	public OrderEventConsumer(PromotionOrderEventHandler handler, ObjectMapper objectMapper) {
		this.handler = handler;
		this.objectMapper = objectMapper;
	}

	/**
	 * 消费订单域事件
	 * Envelope 结构：eventId, eventType, aggregateType, aggregateId, payload, traceId, occurredAt
	 * <p>
	 * 毒消息（解析/字段失败）：抛出 IllegalArgumentException → DLT。
	 * 业务异常：抛出 RuntimeException → retry/backoff → DLT。
	 *
	 * @param message Kafka 消息（JSON 字符串）
	 * @param ack 手动确认
	 */
	@KafkaListener(topics = "${promotion.kafka.topic.order-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void handleOrderEvent(String message, Acknowledgment ack) {
		log.debug("Received order event: {}", message);

		// 解析 Outbox envelope（解析失败 → DLT）
		OrderDomainEventDto event;
		try {
			event = objectMapper.readValue(message, OrderDomainEventDto.class);
		} catch (Exception e) {
			log.error("Failed to parse order event (poison, will go to DLT): message={}", message, e);
			throw new IllegalArgumentException("Invalid JSON for order event", e);
		}

		String eventId = event.getEventId();
		String eventType = event.getEventType();
		String aggregateId = event.getAggregateId();
		String payload = event.getPayload();

		// 校验必需字段（缺失 → DLT）
		if (eventId == null || eventType == null || aggregateId == null) {
			log.error("Invalid order event (missing required fields, will go to DLT): message={}", message);
			throw new IllegalArgumentException("Missing required fields: eventId, eventType, or aggregateId");
		}

		// 根据事件类型分发（业务异常将被抛出，交由 error handler 处理）
		switch (eventType) {
			case "TRADE_PAID":
				handler.handleTradePaid(eventId, aggregateId, payload);
				break;
			case "TRADE_CLOSED":
				handler.handleTradeClosed(eventId, aggregateId, payload);
				break;
			case "PROMOTION_COMMIT":
				// order 侧发布约定：PROMOTION_COMMIT 的 aggregateId 为 quoteId，
				// 真实 tradeId 在 payload 中，需解析后传入（否则回执 tradeId 会错）
				handler.handlePromotionCommit(eventId, resolveCommitTradeId(payload), payload);
				break;
			default:
				log.debug("Ignore event type: {}", eventType);
		}

		// 手动提交 offset
		ack.acknowledge();
		log.debug("Order event processed and acknowledged. eventId={}, eventType={}", eventId, eventType);
	}

	/**
	 * 从 PROMOTION_COMMIT payload 中解析真实 tradeId（payload: quoteId/tradeId/inputHash）。
	 * 解析失败或缺失 tradeId：抛 IllegalArgumentException（毒消息 → DLT）。
	 */
	private String resolveCommitTradeId(String payload) {
		try {
			Object tradeId = objectMapper.readValue(payload, Map.class).get("tradeId");
			if (tradeId == null) {
				throw new IllegalArgumentException("Missing tradeId in PROMOTION_COMMIT payload");
			}
			return String.valueOf(tradeId);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid PROMOTION_COMMIT payload", e);
		}
	}
}
