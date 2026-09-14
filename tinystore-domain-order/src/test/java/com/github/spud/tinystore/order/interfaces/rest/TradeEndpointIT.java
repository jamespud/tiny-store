package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    @Autowired
    private com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository tradeIdempotencyRecordRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        tradeIdempotencyRecordRepository.deleteAll();
        tradeRepository.deleteAll();

        // 清理 Redis 中的幂等键
        Set<String> keys = redisTemplate.keys("idempotency:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
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
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @DisplayName("POST /api/order/trades - the same key replays the same trade even after Redis forgets it")
    void createTrade_sameKeyReplaysAfterRedisLoss() {
        // Review P0-2: the success record used to live only in Redis (written in afterCommit). A Redis
        // restart -- or a Redis failure right after the DB commit -- could therefore let the same
        // Idempotency-Key create a SECOND trade, and could also turn a committed trade into an HTTP error.
        // The durable record makes the replay independent of Redis.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-durable-replay-001");

        String requestBody = "{"
            + "\"tradeId\":\"trade-durable-replay-001\","
            + "\"buyerId\":\"buyer-durable-001\","
            + "\"buyerNick\":\"DurableBuyer\","
            + "\"traceId\":\"trace-durable-001\","
            + "\"orderLines\":[{"
            + "\"skuId\":\"SKU_IT_001\","
            + "\"productId\":\"PROD_IT_001\","
            + "\"productName\":\"Test Product\","
            + "\"shopId\":\"SHOP_IT_001\","
            + "\"sellerId\":\"SELLER_IT_001\","
            + "\"quantity\":1,"
            + "\"priceCents\":9900"
            + "}]"
            + "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> first = restTemplate.exchange("/order/trades", HttpMethod.POST, request, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String firstTradeId = tradeIdOf(first.getBody());

        // Same key + same body while Redis is intact: replay (this is the long-standing behaviour).
        ResponseEntity<String> second = restTemplate.exchange("/order/trades", HttpMethod.POST, request, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tradeIdOf(second.getBody())).isEqualTo(firstTradeId);

        // Simulate Redis losing the key (restart / flush): the durable record must still answer the retry.
        Set<String> keys = redisTemplate.keys("idempotency:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        ResponseEntity<String> third = restTemplate.exchange("/order/trades", HttpMethod.POST, request, String.class);
        assertThat(third.getStatusCode())
            .withFailMessage("a retry after Redis lost the key must replay, not create another trade")
            .isEqualTo(HttpStatus.OK);
        assertThat(tradeIdOf(third.getBody())).isEqualTo(firstTradeId);
        assertThat(tradeRepository.count())
            .withFailMessage("exactly one trade row is expected for this idempotency key")
            .isEqualTo(1L);
    }

    private static String tradeIdOf(String responseBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(responseBody).path("data").path("tradeId").asText();
        }
        catch (Exception e) {
            throw new AssertionError("cannot read tradeId from response: " + responseBody, e);
        }
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

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/cancel")
    @DisplayName("POST /api/order/trades/{tradeId}/cancel - unpaid trade returns 200")
    void cancelTrade_whenUnpaid_returns200() {
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-cancel-it")
            .buyerId("buyer-cancel-it")
            .payStatus("UNPAID")
            .totalAmountCents(10900L)
            .payableAmountCents(10900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-trade-cancel-001");

        HttpEntity<String> request = new HttpEntity<>("{\"reason\":\"test-cancel\"}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades/trade-cancel-it/cancel",
            HttpMethod.POST,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
