package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.kafka.dto.OrderDomainEventDto;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = InventoryApplication.class, properties = {
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"})
@DisplayName("OrderEventConsumer IT")
@SuppressWarnings("resource")
class OrderEventConsumerIT {

    @Container static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:18.1").withDatabaseName("tinystore").withUsername("postgres").withPassword("postgres").withStartupTimeout(Duration.ofMinutes(3));
    @Container static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.4.0")).withExposedPorts(6379);
    @Container static ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1").withEnv("KAFKA_PROCESS_ROLES","broker,controller").withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void r(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("spring.flyway.enabled", () -> "true");
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        reg.add("spring.data.redis.host", redis::getHost);
        reg.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    private static final String TOPIC = "tinystore.order.general";

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private JpaInventoryStockRepository stockRepository;
    @Autowired private JpaInventoryReservationRepository reservationRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        var k = redisTemplate.keys("inventory:*");
        if (k != null && !k.isEmpty()) redisTemplate.delete(k);
        var i = redisTemplate.keys("idem:*");
        if (i != null && !i.isEmpty()) redisTemplate.delete(i);
    }

    private void sendEvent(String eventId, String eventType, String aggregateId, Map<String, Object> payload) throws Exception {
        OrderDomainEventDto dto = new OrderDomainEventDto(
                eventId, eventType, "INVENTORY", aggregateId,
                objectMapper.writeValueAsString(payload),
                "trace-test", LocalDateTime.now().toString());
        String json = objectMapper.writeValueAsString(dto);
        kafkaTemplate.send(TOPIC, aggregateId, json);
    }

    @Test
    @DisplayName("inventoryReserveDbEvent_consumerSavesReservation")
    void inventoryReserveDbEvent_consumerSavesReservation() throws Exception {
        stockRepository.save(new InventoryStockEntity().setShopId("SHOP-ASYNC").setSkuId("SKU-ASYNC")
                .setTotalQuantity(100).setReservedQuantity(0));
        String eventId = UUID.randomUUID().toString();
        String reservationId = "res-async-1";

        sendEvent(eventId, "INVENTORY_RESERVE_DB", reservationId, Map.of(
                "reservationId", reservationId,
                "shopId", "SHOP-ASYNC",
                "skuId", "SKU-ASYNC",
                "quantity", 1,
                "tradeId", "trade-async",
                "orderId", "order-async",
                "expireAt", OffsetDateTime.now().plusMinutes(15).toString()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long count = reservationRepository.sumQuantityByShopSkuStatus("SHOP-ASYNC", "SKU-ASYNC", "PRE_DEDUCTED");
            assertThat(count).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("duplicateEvent_consumerIsIdempotent_savesOnce")
    void duplicateEvent_consumerIsIdempotent_savesOnce() throws Exception {
        stockRepository.save(new InventoryStockEntity().setShopId("SHOP-DUP").setSkuId("SKU-DUP")
                .setTotalQuantity(100).setReservedQuantity(0));
        String eventId = UUID.randomUUID().toString();
        String reservationId = "res-dup-1";

        Map<String, Object> payload = Map.of(
                "reservationId", reservationId,
                "shopId", "SHOP-DUP",
                "skuId", "SKU-DUP",
                "quantity", 1,
                "tradeId", "trade-dup",
                "orderId", "order-dup",
                "expireAt", OffsetDateTime.now().plusMinutes(15).toString());

        sendEvent(eventId, "INVENTORY_RESERVE_DB", reservationId, payload);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long count = reservationRepository.sumQuantityByShopSkuStatus("SHOP-DUP", "SKU-DUP", "PRE_DEDUCTED");
            assertThat(count).isEqualTo(1L);
        });

        // Send duplicate
        sendEvent(eventId, "INVENTORY_RESERVE_DB", reservationId, payload);
        Thread.sleep(3000); // Wait for potential duplicate processing

        long finalCount = reservationRepository.sumQuantityByShopSkuStatus("SHOP-DUP", "SKU-DUP", "PRE_DEDUCTED");
        assertThat(finalCount).isEqualTo(1L); // Still 1, not 2
    }
}
