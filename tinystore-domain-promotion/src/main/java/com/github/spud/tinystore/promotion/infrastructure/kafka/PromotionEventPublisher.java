package com.github.spud.tinystore.promotion.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;

public class PromotionEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public PromotionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(String eventType, String key, Map<String, Object> payload, String traceId) {
        Map<String, Object> event = new HashMap<>(payload);
        event.put("eventType", eventType);
        event.put("traceId", traceId);
        event.put("timestamp", Instant.now().toEpochMilli());
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish promotion event: " + eventType, e);
        }
    }
}
