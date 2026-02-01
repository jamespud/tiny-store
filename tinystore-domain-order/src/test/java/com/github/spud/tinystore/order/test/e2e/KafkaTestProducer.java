package com.github.spud.tinystore.order.test.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaTestProducer implements AutoCloseable {

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;

    public KafkaTestProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    public void sendEvent(String eventType, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("aggregateType", "TRADE");
            envelope.put("aggregateId", payload.getOrDefault("tradeId", ""));
            envelope.put("payload", payloadJson);
            envelope.put("traceId", payload.getOrDefault("traceId", UUID.randomUUID().toString()));
            envelope.put("occurredAt", Instant.now().toString());

            String value = objectMapper.writeValueAsString(envelope);
            producer.send(new ProducerRecord<>(topic, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send kafka event: " + eventType, e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
