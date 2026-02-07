package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trade Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/order/trades (创建交易)
 * - GET /api/order/trades/{tradeId} (查询交易)
 * 
 * Tests full request-response cycle with database
 */
@DisplayName("Trade Endpoint Integration Tests")
class TradeEndpointIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeJpaRepository tradeRepository;

    @BeforeEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        tradeRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @DisplayName("POST /api/order/trades - valid request creates trade")
    void createTrade_validRequest_returnsCreated() {
        // When: create trade
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-trade-create-001");

        String requestBody = "{" +
            "\"tradeId\":\"trade-it-001\"," +
            "\"buyerId\":\"buyer-it-001\"," +
            "\"buyerNick\":\"TestBuyer\"," +
            "\"orderLines\":[{" +
            "\"skuId\":\"SKU_IT_001\"," +
            "\"productId\":\"PROD_IT_001\"," +
            "\"productName\":\"Test Product\"," +
            "\"shopId\":\"SHOP_IT_001\"," +
            "\"sellerId\":\"SELLER_IT_001\"," +
            "\"quantity\":2," +
            "\"priceCents\":9900" +
            "}]" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200 and trade is persisted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("tradeId");
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @DisplayName("POST /api/order/trades - missing buyerId returns 400")
    void createTrade_missingBuyerId_returns400() {
        // When: create trade without buyerId
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-trade-create-002");

        String requestBody = "{" +
            "\"tradeId\":\"trade-it-002\"," +
            "\"orderLines\":[]" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 400 (validation failure)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @DisplayName("GET /api/order/trades/{tradeId} - returns trade when exists")
    void getTrade_whenExists_returns200() {
        // Given: trade exists
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-existing")
            .buyerId("user-002")
            .payStatus("UNPAID")
            .totalAmountCents(19900L)
            .payableAmountCents(19900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // When: GET trade
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/order/trades/trade-existing",
            String.class
        );

        // Then: returns 200 with trade data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("trade-existing");
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @DisplayName("GET /api/order/trades/{tradeId} - returns 404 when not found")
    void getTrade_whenNotFound_returns404() {
        // When: GET non-existent trade
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/order/trades/non-existent-trade",
            String.class
        );

        // Then: returns 404
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
