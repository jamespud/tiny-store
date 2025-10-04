package com.github.spud.tinystore.promotion.infrastructure.kafka;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;

public class PromotionEventPublisher {

	@SuppressWarnings("unused")
	private final KafkaTemplate<String, String> kafkaTemplate;

	public PromotionEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	public void publish(String eventType, String key, Map<String, Object> payload, String traceId) {
		// TODO: Serialize payload to lightweight JSON schema and publish to tinystore.order.general topic.
		Map<String, Object> event = new HashMap<>();
		event.put("eventType", eventType);
		event.put("traceId", traceId);
		event.put("timestamp", Instant.now().toEpochMilli());
		event.put("payload", payload);
		// TODO: Serialize event map and publish via kafkaTemplate.
	}
}
