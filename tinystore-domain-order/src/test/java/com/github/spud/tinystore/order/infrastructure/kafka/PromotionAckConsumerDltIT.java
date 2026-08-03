package com.github.spud.tinystore.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.spud.tinystore.order.OrderApplication;

/**
 * PromotionAckConsumer DLT IT
 *
 * 验证毒消息（解析失败）经 error handler 进入 <topic>.DLT。
 * 依赖 OrderKafkaConsumerConfig 挂载的 CommonErrorHandler。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = OrderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    }
)
@DisplayName("Promotion Ack Consumer DLT IT")
@SuppressWarnings("resource")
class PromotionAckConsumerDltIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.0")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka:4.1.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "order-dlt-it");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("poison message is routed to the .DLT topic")
    void poisonMessage_shouldGoToDlt() {
        String topic = "tinystore.promotion.general";
        String dltTopic = topic + ".DLT";

        // When: 发送毒消息（非法 JSON → PromotionAckConsumer 抛 IllegalArgumentException）
        kafkaTemplate.send(topic, "poison-key", "not-json").join();

        // Then: DLT topic 收到该消息
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<ConsumerRecord<String, String>> records = consumeFrom(dltTopic);
            assertThat(records).anySatisfy(r ->
                assertThat(r.value()).isEqualTo("not-json"));
        });
    }

    private List<ConsumerRecord<String, String>> consumeFrom(String topic) {
        java.util.Properties props = new java.util.Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + System.nanoTime());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            var records = consumer.poll(Duration.ofSeconds(2)).records(topic);
            List<ConsumerRecord<String, String>> result = new java.util.ArrayList<>();
            records.forEach(result::add);
            return result;
        }
    }
}
