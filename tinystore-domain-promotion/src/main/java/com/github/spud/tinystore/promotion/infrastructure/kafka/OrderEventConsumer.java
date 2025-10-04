package com.github.spud.tinystore.promotion.infrastructure.kafka;

import org.springframework.kafka.annotation.KafkaListener;

public class OrderEventConsumer {

	@KafkaListener(topics = "tinystore.order.general", groupId = "promotion-service")
	public void handleOrderEvent(String message) {
		// TODO: Parse eventType from payload and delegate to confirmation/rollback services.
	}
}
