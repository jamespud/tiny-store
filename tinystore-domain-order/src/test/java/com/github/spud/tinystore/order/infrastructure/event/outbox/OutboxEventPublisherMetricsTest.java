package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OutboxEventPublisher Metrics Unit Test
 *
 * 验证 Kafka 发送失败时 counter 递增
 */
class OutboxEventPublisherMetricsTest {

    private OutboxEventPublisher publisher;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxEventService outboxEventService;
    private MeterRegistry meterRegistry;
    private Counter counter;

    @BeforeEach
    void setup() {
        kafkaTemplate = mock(KafkaTemplate.class);
        outboxEventService = mock(OutboxEventService.class);
        meterRegistry = new SimpleMeterRegistry();

        publisher = new OutboxEventPublisher(meterRegistry);
        ReflectionTestUtils.setField(publisher, "kafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "outboxEventService", outboxEventService);
        ReflectionTestUtils.setField(publisher, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "kafkaTopic", "test-topic");

        // 获取注册的 counter
        counter = meterRegistry.find("tinystore.outbox.publish.failure.total").counter();
        assertThat(counter).isNotNull();
    }

    @Test
    void publishEvent_whenKafkaSendFails_shouldIncrementCounter() {
        // Given: KafkaTemplate.send 返回失败的 CompletableFuture
        CompletableFuture<Object> failedFuture = CompletableFuture.failedFuture(
            new RuntimeException("Kafka broker unavailable")
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn((CompletableFuture) failedFuture);

        OutboxEventEntity event = OutboxEventEntity.builder()
            .eventId("test-event-" + UUID.randomUUID())
            .eventType("TEST_EVENT")
            .aggregateType("TestAggregate")
            .aggregateId(UUID.randomUUID().toString())
            .payloadJson("{\"test\":\"data\"}")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        double countBefore = counter.count();

        // When: 调用 publishEvent（异步 exceptionally 会在后台执行）
        publisher.publishEvents(List.of(event));

        // 等待异步 exceptionally 完成（简化：直接等待短时间）
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: counter 递增
        assertThat(counter.count()).isEqualTo(countBefore + 1);
        verify(outboxEventService).markAsFailed(anyString(), anyString());
    }

    @Test
    void publishEvent_whenJsonSerializationFails_shouldIncrementCounter() {
        // Given: ObjectMapper 抛出异常（通过构造一个会导致序列化失败的 entity）
        // 简化：直接让 kafkaTemplate 在准备阶段抛异常前先触发 catch
        // 更直接的方式：mock ObjectMapper
        ObjectMapper faultyMapper = mock(ObjectMapper.class);
        try {
            when(faultyMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("JSON serialization error"));
        } catch (Exception e) {
            // mock setup
        }
        ReflectionTestUtils.setField(publisher, "objectMapper", faultyMapper);

        OutboxEventEntity event = OutboxEventEntity.builder()
            .eventId("test-event-json-fail")
            .eventType("TEST_EVENT")
            .aggregateType("TestAggregate")
            .aggregateId(UUID.randomUUID().toString())
            .payloadJson("{\"test\":\"data\"}")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        double countBefore = counter.count();

        // When: 调用 publishEvent
        publisher.publishEvents(List.of(event));

        // Then: counter 递增（catch 分支）
        assertThat(counter.count()).isEqualTo(countBefore + 1);
        verify(outboxEventService).markAsFailed(anyString(), anyString());
    }
}
