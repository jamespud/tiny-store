package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.domain.port.InventoryReconciliationPort;
import com.github.spud.tinystore.inventory.domain.value.ReconciliationSnapshot;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = InventoryApplication.class, properties = {
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"})
@DisplayName("InventoryReconciliationPort IT")
@SuppressWarnings("resource")
class InventoryReconciliationPortIT {

    @Container static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:18.1").withDatabaseName("tinystore")
            .withUsername("postgres").withPassword("postgres")
            .withStartupTimeout(Duration.ofMinutes(3));
    @Container static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.4.0")).withExposedPorts(6379);
    @Container static ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withStartupTimeout(Duration.ofMinutes(3));

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

    @Autowired private InventoryReconciliationPort reconciliationPort;
    @Autowired private JpaInventoryStockRepository stockRepository;
    @Autowired private JpaInventoryReservationRepository reservationRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String SHOP = "SHOP-REC";
    private final OffsetDateTime future = OffsetDateTime.now().plusMinutes(30);

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        var k = redisTemplate.keys("inventory:*");
        if (k != null && !k.isEmpty()) redisTemplate.delete(k);
    }

    private void seedStock(long total) {
        stockRepository.save(new InventoryStockEntity().setShopId(SHOP).setSkuId("sku-1")
                .setTotalQuantity(total).setReservedQuantity(0));
    }

    private void seedReservation(String status, long qty) {
        reservationRepository.save(new InventoryReservationEntity()
                .setReservationId(status + "-" + System.nanoTime())
                .setShopId(SHOP).setSkuId("sku-1").setQuantity(qty).setStatus(status)
                .setExpireAt(future).setTradeId("t").setOperationId("op"));
    }

    @Test
    @DisplayName("snapshot_admissionUnderCounted_flagsOversellRisk")
    void snapshot_admissionUnderCounted_flagsOversellRisk() {
        seedStock(100);
        seedReservation("PRE_DEDUCTED", 5);     // DB has 5 in-flight
        redisTemplate.opsForValue().set("inventory:total:" + SHOP + ":sku-1", "100");
        redisTemplate.opsForValue().set("inventory:deducted:" + SHOP + ":sku-1", "2"); // Redis thinks only 2 deducted
        ReconciliationSnapshot s = reconciliationPort.snapshot(SHOP, "sku-1");
        assertThat(s.isAdmissionUnderCounted()).isTrue();   // OVERSELL RISK
        assertThat(s.isTotalDesync()).isFalse();
    }

    @Test
    @DisplayName("snapshot_totalDesync_flagsWhenRedisTotalHigherThanDb")
    void snapshot_totalDesync_flagsWhenRedisTotalHigherThanDb() {
        seedStock(100);
        redisTemplate.opsForValue().set("inventory:total:" + SHOP + ":sku-1", "150"); // Redis inflated -> oversell risk
        ReconciliationSnapshot s = reconciliationPort.snapshot(SHOP, "sku-1");
        assertThat(s.isTotalDesync()).isTrue();
    }

    @Test
    @DisplayName("snapshot_admissionOverCounted_flagsLostSalesRisk")
    void snapshot_admissionOverCounted_flagsLostSalesRisk() {
        seedStock(100);
        seedReservation("PRE_DEDUCTED", 2);
        redisTemplate.opsForValue().set("inventory:total:" + SHOP + ":sku-1", "100");
        redisTemplate.opsForValue().set("inventory:deducted:" + SHOP + ":sku-1", "9"); // Redis over-counted
        ReconciliationSnapshot s = reconciliationPort.snapshot(SHOP, "sku-1");
        assertThat(s.isAdmissionOverCounted()).isTrue();
    }

    @Test
    @DisplayName("snapshot_clean_whenAllInSync")
    void snapshot_clean_whenAllInSync() {
        seedStock(100);
        seedReservation("PRE_DEDUCTED", 5);
        redisTemplate.opsForValue().set("inventory:total:" + SHOP + ":sku-1", "100");
        redisTemplate.opsForValue().set("inventory:deducted:" + SHOP + ":sku-1", "5");
        ReconciliationSnapshot s = reconciliationPort.snapshot(SHOP, "sku-1");
        assertThat(s.isTotalDesync()).isFalse();
        assertThat(s.isAdmissionUnderCounted()).isFalse();
        assertThat(s.isAdmissionOverCounted()).isFalse();
        assertThat(s.isNegativeAvailable()).isFalse();
    }
}
