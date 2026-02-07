package com.github.spud.tinystore.promotion.interfaces.rest;

import com.github.spud.tinystore.promotion.PromotionApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

/**
 * Checkout Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/promotion/checkout/quote
 * - POST /api/promotion/checkout/release
 * 
 * Validates promotion endpoints with real database (Testcontainers)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = PromotionApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    }
)
@DisplayName("Checkout Endpoint IT")
@SuppressWarnings("resource")
class CheckoutEndpointIT {

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

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/quote")
    @DisplayName("POST /api/promotion/checkout/quote - returns quote")
    void quote_withRequest_returnsQuote() {
        // Given: quote request
        
        // When: request quote
        Map<String, Object> request = Map.of(
            "userId", "user-promo-" + UUID.randomUUID(),
            "addressId", "addr-" + UUID.randomUUID(),
            "lines", List.of(Map.of(
                "skuId", "SKU_PROMO",
                "shopId", "SHOP_PROMO",
                "quantity", 2,
                "baseUnitPriceCents", 10000,
                "weightGrams", 200
            ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-quote-" + UUID.randomUUID());

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/promotion/checkout/quote",
            new HttpEntity<>(request, headers),
            Map.class
        );

        // Then: success with discount
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("discountAmount");
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/release")
    @DisplayName("POST /api/promotion/checkout/release - accepts release request")
    void release_afterQuote_returnsSuccess() {
        // Given: a prior quote (simplified - just verify endpoint accepts request)
        
        // When: release
        Map<String, Object> releaseRequest = Map.of(
            "quoteId", "quote-" + UUID.randomUUID(),
            "tradeId", "trade-" + UUID.randomUUID(),
            "reason", "order-cancelled"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-rel-" + UUID.randomUUID());

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/promotion/checkout/release",
            new HttpEntity<>(releaseRequest, headers),
            Map.class
        );

        // Then: success
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
