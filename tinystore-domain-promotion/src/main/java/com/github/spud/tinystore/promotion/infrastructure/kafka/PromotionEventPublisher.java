package com.github.spud.tinystore.promotion.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * 发布回执事件。事务激活时注册 afterCommit 回调、提交后发送（事务回滚则不发送，
     * 避免"回执已发出但处理事务回滚"的矛盾）；无事务上下文（测试/独立调用）直接发送。
     */
    public void publish(String eventType, String key, Map<String, Object> payload, String traceId) {
        Map<String, Object> event = new HashMap<>(payload);
        event.put("eventType", eventType);
        event.put("traceId", traceId);
        event.put("timestamp", Instant.now().toEpochMilli());
        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish promotion event: " + eventType, e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, json);
                }
            });
        } else {
            kafkaTemplate.send(topic, key, json);
        }
    }
}
