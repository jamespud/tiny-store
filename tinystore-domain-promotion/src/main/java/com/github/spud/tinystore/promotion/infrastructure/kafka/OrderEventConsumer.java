package com.github.spud.tinystore.promotion.infrastructure.kafka;

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
	 *
	 * @param message Kafka 消息（JSON 字符串）
	 * @param ack 手动确认
	 */
	@KafkaListener(topics = "${promotion.kafka.topic.order-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void handleOrderEvent(String message, Acknowledgment ack) {
		try {
			log.debug("Received order event: {}", message);

			// 解析 Outbox envelope
			OrderDomainEventDto event = objectMapper.readValue(message, OrderDomainEventDto.class);
			String eventId = event.getEventId();
			String eventType = event.getEventType();
			String aggregateId = event.getAggregateId();
			String payload = event.getPayload();

			// 根据事件类型分发
			switch (eventType) {
				case "TRADE_PAID":
					handler.handleTradePaid(eventId, aggregateId, payload);
					break;
				case "TRADE_CLOSED":
					handler.handleTradeClosed(eventId, aggregateId, payload);
					break;
				default:
					log.debug("Ignore event type: {}", eventType);
			}

			// 手动提交 offset
			ack.acknowledge();
			log.debug("Order event processed and acknowledged. eventId={}, eventType={}", eventId, eventType);

		} catch (Exception e) {
			log.error("Failed to handle order event: message={}", message, e);
			// 消费失败不提交 offset，等待重试
			// 生产环境可配置死信队列或告警
		}
	}
}
