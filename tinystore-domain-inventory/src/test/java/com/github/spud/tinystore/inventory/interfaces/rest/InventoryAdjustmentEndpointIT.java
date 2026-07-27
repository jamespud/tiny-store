package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = InventoryApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    })
@DisplayName("InventoryAdjustment Endpoint IT")
@SuppressWarnings("resource")
class InventoryAdjustmentEndpointIT {

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
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JpaInventoryStockRepository stockRepository;
    @Autowired private JpaInventoryAdjustmentRepository adjustmentRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        adjustmentRepository.deleteAll();
        stockRepository.deleteAll();
        var inv = redisTemplate.keys("inventory:*");
        if (inv != null && !inv.isEmpty()) redisTemplate.delete(inv);
    }

    private HttpHeaders jsonHeaders(String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", key);
        return h;
    }

    @Test
    @DisplayName("adjust_refund_increasesDbTotalAndRedisTotal")
    void adjust_refund_increasesDbTotalAndRedisTotal() {
        String shop = "SHOP-ADJ-1", sku = "SKU-ADJ-1";
        stockRepository.save(new InventoryStockEntity().setShopId(shop).setSkuId(sku)
                .setTotalQuantity(100).setReservedQuantity(0));
        String refundId = "refund-" + UUID.randomUUID();

        ResponseEntity<Map> r = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/adjustments",
            new HttpEntity<>(Map.of(
                "reason", "RESTOCK_REFUND",
                "referenceId", refundId,
                "tradeId", "trade-1",
                "items", List.of(Map.of("shopId", shop, "skuId", sku, "delta", 5))
            ), jsonHeaders("adj-" + UUID.randomUUID())),
            Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("success")).isEqualTo(true);
        assertThat(stockRepository.findByShopIdAndSkuId(shop, sku).get().getTotalQuantity()).isEqualTo(105L);
        String totalKey = "inventory:total:" + shop + ":" + sku;
        assertThat(Long.parseLong(redisTemplate.opsForValue().get(totalKey))).isEqualTo(105L);
    }

    @Test
    @DisplayName("adjust_multiSku_writesPerSkuAdjustmentRows")
    void adjust_multiSku_writesPerSkuAdjustmentRows() {
        String shop = "SHOP-ADJ-2";
        stockRepository.save(new InventoryStockEntity().setShopId(shop).setSkuId("sku-A").setTotalQuantity(10).setReservedQuantity(0));
        stockRepository.save(new InventoryStockEntity().setShopId(shop).setSkuId("sku-B").setTotalQuantity(20).setReservedQuantity(0));
        String refundId = "refund-multi-" + UUID.randomUUID();

        ResponseEntity<Map> r = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/adjustments",
            new HttpEntity<>(Map.of(
                "reason", "RESTOCK_REFUND",
                "referenceId", refundId,
                "tradeId", "trade-2",
                "items", List.of(
                    Map.of("shopId", shop, "skuId", "sku-A", "delta", 2),
                    Map.of("shopId", shop, "skuId", "sku-B", "delta", 3))
            ), jsonHeaders("adj-" + UUID.randomUUID())),
            Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("success")).isEqualTo(true);
        assertThat(stockRepository.findByShopIdAndSkuId(shop, "sku-A").get().getTotalQuantity()).isEqualTo(12L);
        assertThat(stockRepository.findByShopIdAndSkuId(shop, "sku-B").get().getTotalQuantity()).isEqualTo(23L);
        // V8 constraint allows per-SKU rows for one (reason, referenceId):
        assertThat(adjustmentRepository.countByReasonAndReferenceId("RESTOCK_REFUND", refundId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("adjust_duplicateRefundId_isIdempotent_noDoubleRestock")
    void adjust_duplicateRefundId_isIdempotent_noDoubleRestock() {
        String shop = "SHOP-ADJ-3", sku = "SKU-ADJ-3";
        stockRepository.save(new InventoryStockEntity().setShopId(shop).setSkuId(sku).setTotalQuantity(100).setReservedQuantity(0));
        String refundId = "refund-dup-" + UUID.randomUUID();
        Map<String, Object> body = Map.of(
            "reason", "RESTOCK_REFUND", "referenceId", refundId, "tradeId", "trade-3",
            "items", List.of(Map.of("shopId", shop, "skuId", sku, "delta", 5)));

        restTemplate.postForEntity("http://localhost:" + port + "/api/inventory/adjustments",
            new HttpEntity<>(body, jsonHeaders("adj-a-" + UUID.randomUUID())), Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/adjustments",
            new HttpEntity<>(body, jsonHeaders("adj-b-" + UUID.randomUUID())), Map.class);

        assertThat(second.getBody().get("success")).isEqualTo(true);
        assertThat(stockRepository.findByShopIdAndSkuId(shop, sku).get().getTotalQuantity()).isEqualTo(105L);
        assertThat(redisTemplate.opsForValue().get("inventory:total:" + shop + ":" + sku)).isEqualTo("105");
    }
}
