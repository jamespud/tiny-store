package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.InventoryApplication;
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
import org.springframework.http.HttpMethod;
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
@SpringBootTest(classes = InventoryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"})
@DisplayName("Inventory Observability (Metrics + Trace) IT")
@SuppressWarnings("resource")
class InventoryObservabilityIT {

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

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JpaInventoryStockRepository stockRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        stockRepository.deleteAll();
        var k = redisTemplate.keys("inventory:*");
        if (k != null && !k.isEmpty()) redisTemplate.delete(k);
        var i = redisTemplate.keys("idem:*");
        if (i != null && !i.isEmpty()) redisTemplate.delete(i);
    }

    private HttpHeaders jsonHeaders(String idemKey, String traceId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idemKey);
        if (traceId != null) h.add("X-Trace-Id", traceId);
        return h;
    }

    private String prometheusBody() {
        ResponseEntity<String> prom = restTemplate.exchange(
            "http://localhost:" + port + "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(prom.getStatusCode()).isEqualTo(HttpStatus.OK);
        return prom.getBody();
    }

    @Test
    @DisplayName("reserve_success_counter_exposed_on_prometheus_endpoint")
    void reserve_success_counter_exposed_on_prometheus_endpoint() {
        stockRepository.save(new InventoryStockEntity().setShopId("SHOP-OBS").setSkuId("SKU-OBS").setTotalQuantity(100).setReservedQuantity(0));
        String tradeId = "trade-" + UUID.randomUUID();
        String orderId = "order-" + UUID.randomUUID();
        restTemplate.postForEntity("http://localhost:" + port + "/api/inventory/reservations/reserve",
            new HttpEntity<>(Map.of("tradeId", tradeId, "orderId", orderId,
                "items", List.of(Map.of("shopId", "SHOP-OBS", "skuId", "SKU-OBS", "quantity", 1))),
                jsonHeaders("idem-" + UUID.randomUUID(), "trace-1")), Map.class);

        assertThat(prometheusBody()).contains("tinystore_inventory_reserve_success_total");
    }

    @Test
    @DisplayName("adjust_counter_exposed_on_prometheus_endpoint")
    void adjust_counter_exposed_on_prometheus_endpoint() {
        stockRepository.save(new InventoryStockEntity().setShopId("SHOP-ADJ-OBS").setSkuId("SKU-ADJ-OBS").setTotalQuantity(100).setReservedQuantity(0));
        restTemplate.postForEntity("http://localhost:" + port + "/api/inventory/adjustments",
            new HttpEntity<>(Map.of("reason", "RESTOCK_REFUND", "referenceId", "ref-" + UUID.randomUUID(),
                "tradeId", "trade-obs", "items", List.of(Map.of("shopId", "SHOP-ADJ-OBS", "skuId", "SKU-ADJ-OBS", "delta", 5))),
                jsonHeaders("adj-" + UUID.randomUUID(), null)), Map.class);

        assertThat(prometheusBody()).contains("tinystore_inventory_adjust_total");
    }

    @Test
    @DisplayName("x_trace_id_header_echoed_on_response")
    void x_trace_id_header_echoed_on_response() {
        stockRepository.save(new InventoryStockEntity().setShopId("SHOP-TR").setSkuId("SKU-TR").setTotalQuantity(100).setReservedQuantity(0));
        ResponseEntity<Map> res = restTemplate.postForEntity("http://localhost:" + port + "/api/inventory/reservations/reserve",
            new HttpEntity<>(Map.of("tradeId", "trade-tr", "orderId", "order-tr",
                "items", List.of(Map.of("shopId", "SHOP-TR", "skuId", "SKU-TR", "quantity", 1))),
                jsonHeaders("idem-tr-" + UUID.randomUUID(), "trace-explicit-42")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-explicit-42");
    }
}
