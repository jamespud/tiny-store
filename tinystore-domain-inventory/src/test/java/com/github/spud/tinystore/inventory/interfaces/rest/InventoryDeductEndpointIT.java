package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * InventoryDeduct Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/inventory/deduct → POST /api/inventory/release (normal flow)
 * - DB record failure triggers Redis compensation rollback
 * - Idempotency misuse protection (release not short-circuited by deduct cache)
 * 
 * Uses Testcontainers (Postgres, Redis, Kafka) to validate real Redis state
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = InventoryApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    }
)
@DisplayName("InventoryDeduct Endpoint IT")
@SuppressWarnings("resource")
class InventoryDeductEndpointIT {

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
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private InventoryDeductRecordRepository mockRecordRepository;

    @BeforeEach
    void cleanup() {
        // Reset mock before each test
        reset(mockRecordRepository);
        
        stockRepository.deleteAll();
        // Clean Redis keys matching inventory:*
        var keys = redisTemplate.keys("inventory:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys("idem:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/deduct")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/release")
    @DisplayName("Normal flow: deduct → release with Redis state verification")
    void normalFlow_deductThenRelease_redisStateVerified() {
        // Given: stock exists
        String shopId = "SHOP-IT-1";
        String skuId1 = "SKU-IT-001";
        String skuId2 = "SKU-IT-002";
        
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId1)
            .setTotalQuantity(100)
            .setReservedQuantity(0));
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId2)
            .setTotalQuantity(50)
            .setReservedQuantity(0));

        // When: deduct
        String orderId = "ORDER-IT-" + UUID.randomUUID();
        Map<String, Object> deductRequest = Map.of(
            "orderId", orderId,
            "items", List.of(
                Map.of("shopId", shopId, "skuId", skuId1, "quantity", 10),
                Map.of("shopId", shopId, "skuId", skuId2, "quantity", 5)
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-deduct-" + UUID.randomUUID());

        ResponseEntity<Map> deductResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/deduct",
            new HttpEntity<>(deductRequest, headers),
            Map.class
        );

        // Then: deduct success
        assertThat(deductResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deductResponse.getBody()).isNotNull();
        assertThat(deductResponse.getBody().get("success")).isEqualTo(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> occupyPairs = (List<Map<String, String>>) deductResponse.getBody().get("occupyPairs");
        assertThat(occupyPairs).hasSize(2);

        // Then: Redis state verification - uncommit members should exist
        String uncommitKey1 = String.format("inventory:uncommit:%s:%s", shopId, skuId1);
        String uncommitKey2 = String.format("inventory:uncommit:%s:%s", shopId, skuId2);
        String occupyId1 = occupyPairs.stream()
                .filter(p -> skuId1.equals(p.get("skuId")))
                .findFirst().orElseThrow()
                .get("occupyId");
        String occupyId2 = occupyPairs.stream()
                .filter(p -> skuId2.equals(p.get("skuId")))
                .findFirst().orElseThrow()
                .get("occupyId");
        
        Double score1Before = redisTemplate.opsForZSet().score(uncommitKey1, occupyId1);
        Double score2Before = redisTemplate.opsForZSet().score(uncommitKey2, occupyId2);
        assertThat(score1Before).isNotNull(); // Member exists
        assertThat(score2Before).isNotNull();

        String deductedKey1 = String.format("inventory:deducted:%s:%s", shopId, skuId1);
        String deductedKey2 = String.format("inventory:deducted:%s:%s", shopId, skuId2);
        String deducted1Before = redisTemplate.opsForValue().get(deductedKey1);
        String deducted2Before = redisTemplate.opsForValue().get(deductedKey2);
        assertThat(deducted1Before).isEqualTo("10");
        assertThat(deducted2Before).isEqualTo("5");

        // When: release (using DIFFERENT idempotency key)
        Map<String, Object> releaseRequest = Map.of(
            "orderId", orderId,
            "reason", "test-cleanup",
            "occupyPairs", occupyPairs
        );

        HttpHeaders releaseHeaders = new HttpHeaders();
        releaseHeaders.setContentType(MediaType.APPLICATION_JSON);
        releaseHeaders.add("Idempotency-Key", "idem-release-" + UUID.randomUUID()); // Different key

        ResponseEntity<Map> releaseResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/release",
            new HttpEntity<>(releaseRequest, releaseHeaders),
            Map.class
        );

        // Then: release success
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(releaseResponse.getBody().get("success")).isEqualTo(true);

        // Then: Redis state verification - members should be removed
        Double score1After = redisTemplate.opsForZSet().score(uncommitKey1, occupyId1);
        Double score2After = redisTemplate.opsForZSet().score(uncommitKey2, occupyId2);
        assertThat(score1After).isNull(); // Member removed
        assertThat(score2After).isNull();

        String deducted1After = redisTemplate.opsForValue().get(deductedKey1);
        String deducted2After = redisTemplate.opsForValue().get(deductedKey2);
        assertThat(deducted1After).isIn("0", null); // Rolled back
        assertThat(deducted2After).isIn("0", null);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/deduct")
    @DisplayName("DB record failure triggers Redis compensation rollback")
    void dbRecordFailure_triggersRedisCompensation() {
        // Given: stock exists
        String shopId = "SHOP-IT-2";
        String skuId = "SKU-IT-COMP";
        
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId)
            .setTotalQuantity(100)
            .setReservedQuantity(0));

        // Mock DB record to fail
        doThrow(new RuntimeException("DB write failed"))
            .when(mockRecordRepository).saveDeducted(anyString(), anyString(), anyList(), anyList());

        // When: deduct
        String orderId = "ORDER-IT-DB-FAIL-" + UUID.randomUUID();
        Map<String, Object> deductRequest = Map.of(
            "orderId", orderId,
            "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 20))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-dbfail-" + UUID.randomUUID());

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/deduct",
            new HttpEntity<>(deductRequest, headers),
            Map.class
        );

        // Then: deduct fails
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(false);
        assertThat(response.getBody().get("message")).isEqualTo("DB_RECORD_FAILED");

        // Then: Redis should NOT have residual uncommit members (compensated)
        String uncommitKey = String.format("inventory:uncommit:%s:%s", shopId, skuId);
        Long memberCount = redisTemplate.opsForZSet().zCard(uncommitKey);
        assertThat(memberCount).isIn(0L, null);

        // Then: deducted should be rolled back to 0
        String deductedKey = String.format("inventory:deducted:%s:%s", shopId, skuId);
        String deducted = redisTemplate.opsForValue().get(deductedKey);
        assertThat(deducted).isIn("0", null);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/release")
    @DisplayName("Idempotency misuse: release execution not short-circuited by deduct cache")
    void idempotencyMisuse_releaseStillExecutes_notShortCircuited() {
        // Given: stock exists
        String shopId = "SHOP-IT-3";
        String skuId = "SKU-IT-IDEM";
        
        stockRepository.save(new InventoryStockEntity()
            .setShopId(shopId)
            .setSkuId(skuId)
            .setTotalQuantity(100)
            .setReservedQuantity(0));

        // When: deduct with idempotency key K
        String sharedIdemKey = "shared-idem-" + UUID.randomUUID(); // SAME key used for both!
        String orderId = "ORDER-IT-SHARED-" + UUID.randomUUID();
        
        Map<String, Object> deductRequest = Map.of(
            "orderId", orderId,
            "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 15))
        );

        HttpHeaders deductHeaders = new HttpHeaders();
        deductHeaders.setContentType(MediaType.APPLICATION_JSON);
        deductHeaders.add("Idempotency-Key", sharedIdemKey);

        ResponseEntity<Map> deductResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/deduct",
            new HttpEntity<>(deductRequest, deductHeaders),
            Map.class
        );

        assertThat(deductResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deductResponse.getBody().get("success")).isEqualTo(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> occupyPairs = (List<Map<String, String>>) deductResponse.getBody().get("occupyPairs");
        String occupyId = occupyPairs.get(0).get("occupyId");

        // Verify member exists in Redis before release
        String uncommitKey = String.format("inventory:uncommit:%s:%s", shopId, skuId);
        Double scoreBefore = redisTemplate.opsForZSet().score(uncommitKey, occupyId);
        assertThat(scoreBefore).isNotNull();

        // When: release using THE SAME idempotency key (incorrect reuse)
        Map<String, Object> releaseRequest = Map.of(
            "orderId", orderId,
            "reason", "test-shared-key",
            "occupyPairs", occupyPairs
        );

        HttpHeaders releaseHeaders = new HttpHeaders();
        releaseHeaders.setContentType(MediaType.APPLICATION_JSON);
        releaseHeaders.add("Idempotency-Key", sharedIdemKey); // SAME KEY!

        ResponseEntity<Map> releaseResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/inventory/release",
            new HttpEntity<>(releaseRequest, releaseHeaders),
            Map.class
        );

        // Then: release should still succeed (not short-circuited)
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(releaseResponse.getBody().get("success")).isEqualTo(true);

        // CRITICAL: Redis member should be removed (rollback executed despite shared key)
        Double scoreAfter = redisTemplate.opsForZSet().score(uncommitKey, occupyId);
        assertThat(scoreAfter).as("Release should remove member even with shared idempotency key")
                .isNull();

        String deductedKey = String.format("inventory:deducted:%s:%s", shopId, skuId);
        String deducted = redisTemplate.opsForValue().get(deductedKey);
        assertThat(deducted).isIn("0", null);
    }
}
