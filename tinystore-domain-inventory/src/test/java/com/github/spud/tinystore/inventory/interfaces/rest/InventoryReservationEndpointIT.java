package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
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
    }
)
@DisplayName("InventoryReservation Endpoint IT")
@SuppressWarnings("resource")
class InventoryReservationEndpointIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
        .withDatabaseName("tinystore")
        .withUsername("postgres")
        .withPassword("postgres")
        .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
        .withExposedPorts(6379);

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JpaInventoryStockRepository stockRepository;

    @Autowired
    private JpaInventoryReservationRepository reservationRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        var inventoryKeys = redisTemplate.keys("inventory:*");
        if (inventoryKeys != null && !inventoryKeys.isEmpty()) {
            redisTemplate.delete(inventoryKeys);
        }
        var idemKeys = redisTemplate.keys("idem:*");
        if (idemKeys != null && !idemKeys.isEmpty()) {
            redisTemplate.delete(idemKeys);
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/reserve")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @DisplayName("Canonical flow: reserve then confirm returns success")
    void canonicalFlow_reserveThenConfirm_returnsSuccess() {
        String shopId = "SHOP-RES-1";
        String skuId = "SKU-RES-1";
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId)
            .setTotalQuantity(100)
            .setReservedQuantity(0));

        String tradeId = "trade-res-" + UUID.randomUUID();
        String orderId = "order-res-" + UUID.randomUUID();

        ResponseEntity<Map> reserveResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/reservations/reserve",
            new HttpEntity<>(Map.of(
                "tradeId", tradeId,
                "orderId", orderId,
                "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 2))
            ), jsonHeaders("reserve-key-" + UUID.randomUUID())),
            Map.class
        );

        assertThat(reserveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reserveResponse.getBody()).isNotNull();
        assertThat(reserveResponse.getBody().get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> occupyPairs = (List<Map<String, Object>>) reserveResponse.getBody().get("occupyPairs");
        assertThat(occupyPairs).hasSize(1);

        ResponseEntity<Map> confirmResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/reservations/confirm",
            new HttpEntity<>(Map.of(
                "paymentId", "pay-" + UUID.randomUUID(),
                "tradeId", tradeId,
                "orderId", orderId,
                "occupyPairs", occupyPairs
            ), jsonHeaders("confirm-key-" + UUID.randomUUID())),
            Map.class
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResponse.getBody()).isNotNull();
        assertThat(confirmResponse.getBody().get("success")).isEqualTo(true);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/reserve")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/release")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @DisplayName("Released reservation confirm returns conflict")
    void releasedReservation_confirmReturnsConflict() {
        String shopId = "SHOP-REL-1";
        String skuId = "SKU-REL-1";
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId)
            .setTotalQuantity(100)
            .setReservedQuantity(0));

        String tradeId = "trade-rel-" + UUID.randomUUID();
        String orderId = "order-rel-" + UUID.randomUUID();

        ResponseEntity<Map> reserveResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/reservations/reserve",
            new HttpEntity<>(Map.of(
                "tradeId", tradeId,
                "orderId", orderId,
                "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 1))
            ), jsonHeaders("reserve-key-" + UUID.randomUUID())),
            Map.class
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> occupyPairs = (List<Map<String, Object>>) reserveResponse.getBody().get("occupyPairs");
        assertThat(occupyPairs).hasSize(1);

        ResponseEntity<Map> releaseResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/reservations/release",
            new HttpEntity<>(Map.of(
                "orderId", orderId,
                "reason", "cancelled-before-payment",
                "occupyPairs", occupyPairs
            ), jsonHeaders("release-key-" + UUID.randomUUID())),
            Map.class
        );

        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(releaseResponse.getBody()).isNotNull();
        assertThat(releaseResponse.getBody().get("success")).isEqualTo(true);

        ResponseEntity<Map> confirmResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/reservations/confirm",
            new HttpEntity<>(Map.of(
                "paymentId", "pay-" + UUID.randomUUID(),
                "tradeId", tradeId,
                "orderId", orderId,
                "occupyPairs", occupyPairs
            ), jsonHeaders("confirm-key-" + UUID.randomUUID())),
            Map.class
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResponse.getBody()).isNotNull();
        assertThat(confirmResponse.getBody().get("success")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> conflictReservationIds = (List<String>) confirmResponse.getBody().get("conflictReservationIds");
        assertThat(conflictReservationIds).isNotEmpty();
    }

    private HttpHeaders jsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }
}