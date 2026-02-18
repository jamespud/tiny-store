package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox Publisher Kafka Down Chaos Integration Test
 *
 * 测试策略：
 * - 将 Kafka bootstrap-servers 指向不可达地址（Kafka 不启动）
 * - 插入 PENDING outbox 事件
 * - 直接调用 publisher.publishEvent 触发发布（3 次）
 * - 验证状态机：retryCount 递增，达阈值后 status=FAILED
 */
@SpringBootTest(
    classes = OrderApplication.class,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        // Kafka 指向不可达地址（127.0.0.1:1 通常拒绝连接）
        "spring.kafka.bootstrap-servers=127.0.0.1:1",
        "spring.kafka.producer.properties.max.block.ms=2000"  // 快速失败
    }
)
@Testcontainers
class OutboxPublisherKafkaDownChaosIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private OutboxEventPublisher publisher;

    @Autowired
    private OutboxEventJpaRepository repository;

    @BeforeEach
    void setup() {
        repository.deleteAll();
    }

    @Test
    void publishEvent_whenKafkaUnreachable_shouldIncrementRetryCountAndEventuallyFail() {
        // Given: 插入一条 PENDING outbox 事件
        String eventId = "chaos-kafka-down-" + UUID.randomUUID();
        OutboxEventEntity event = OutboxEventEntity.builder()
            .eventId(eventId)
            .eventType("TEST_EVENT")
            .aggregateType("TestAggregate")
            .aggregateId(UUID.randomUUID().toString())
            .payloadJson("{\"test\":\"kafka-down\"}")
            .status("PENDING")
            .traceId("test-trace")
            .createdAt(LocalDateTime.now())
            .retryCount(0)
            .build();
        repository.save(event);

        // When: 尝试发布 3 次（Kafka 不可达，会触发 exceptionally 回调）
        // 注意：publisher.publishEvent 的 KafkaTemplate.send 返回 CompletableFuture，
        // exceptionally 回调会调用 markAsFailed，每次递增 retryCount，>=3 时置 FAILED
        for (int i = 1; i <= 3; i++) {
            OutboxEventEntity current = repository.findByEventId(eventId).orElseThrow();
            publisher.publishEvent(current);

            // 等待异步 exceptionally 回调完成（最多 10 秒）
            final int attempt = i;
            Awaitility.await()
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    OutboxEventEntity updated = repository.findByEventId(eventId).orElseThrow();
                    assertThat(updated.getRetryCount()).isGreaterThanOrEqualTo(attempt);
                });
        }

        // Then: 验证最终状态
        OutboxEventEntity finalEvent = repository.findByEventId(eventId).orElseThrow();

        // retryCount 应 >= 3
        assertThat(finalEvent.getRetryCount()).isGreaterThanOrEqualTo(3);

        // status 应为 FAILED（按当前 OutboxEventService.markAsFailed 逻辑：retryCount >= 3 置 FAILED）
        assertThat(finalEvent.getStatus()).isEqualTo("FAILED");

        // lastError 应非空
        assertThat(finalEvent.getLastError()).isNotNull();
        assertThat(finalEvent.getLastError()).isNotEmpty();
    }
}
