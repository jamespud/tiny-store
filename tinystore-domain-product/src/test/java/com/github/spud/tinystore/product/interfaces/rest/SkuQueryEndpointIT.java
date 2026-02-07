package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.ProductApplication;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaProductRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaSkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SKU Query Endpoint Integration Test
 * 
 * Coverage: GET /api/skus/{skuId} (external contract path)
 * 
 * Validates endpoint behavior with real database (Testcontainers)
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = ProductApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    }
)
@DisplayName("SKU Query Endpoint IT")
@SuppressWarnings("resource")
class SkuQueryEndpointIT {

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
    private JpaSkuRepository skuRepository;

    @Autowired
    private JpaProductRepository productRepository;

    @BeforeEach
    void cleanup() {
        skuRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:product:GET:/api/skus/{skuId}")
    @DisplayName("GET /api/skus/{skuId} - SKU exists, returns 200 OK")
    void getSku_whenExists_returns200() {
        // Given: SKU exists in DB
        String shopId = "SHOP_TEST";
        String productId = "prod-001";
        String skuId = "sku-001";

        ProductEntity product = new ProductEntity();
        product.setShopId(shopId);
        product.setProductId(productId);
        product.setName("Test Product");
        product.setStatus("ACTIVE");
        productRepository.save(product);

        SkuEntity sku = new SkuEntity();
        sku.setShopId(shopId);
        sku.setSkuId(skuId);
        sku.setProductId(productId);
        sku.setSkuName("Test SKU");
        sku.setSpecCombination("color:red");
        sku.setSpecJson("{}");
        sku.setUnitPriceCents(1000L);
        sku.setPromotePriceCents(900L);
        sku.setWeightGrams(100L);
        sku.setStatus("ACTIVE");
        skuRepository.save(sku);

        // When: GET /api/skus/{skuId}
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Shop-Id", shopId);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/skus/" + skuId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(skuId);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:product:GET:/api/skus/{skuId}")
    @DisplayName("GET /api/skus/{skuId} - SKU not found, returns 404")
    void getSku_whenNotExists_returns404() {
        // Given: SKU does not exist
        String shopId = "SHOP_TEST";
        String nonExistentSkuId = "non-existent-sku";

        // When: GET /api/skus/{skuId}
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Shop-Id", shopId);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/skus/" + nonExistentSkuId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: 404 NOT FOUND
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
