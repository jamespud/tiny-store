package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Trade Endpoint Redis Failure Integration Test
 *
 * Tests behavior when Redis/Idempotency service is unavailable
 */
@DisplayName("Trade Endpoint Redis Failure Tests")
// This class swaps the real IdempotencyService for a throwing mock. The ITs share Spring's context cache,
// so without this the mock (and Redis being "down") can leak into the classes that run after it -- they
// then fail in @BeforeEach instead of exercising anything. Close the context when this class is done.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TradeEndpointRedisFailureIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeJpaRepository tradeRepository;

    @MockitoBean
    private IdempotencyService idempotencyServiceMock;

    @BeforeEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        tradeRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades:redis-failure")
    @DisplayName("POST /api/order/trades - Redis unavailable returns 503")
    void createTrade_whenRedisUnavailable_returns503() {
        // Given: IdempotencyService throws IdempotencyServiceUnavailableException (simulating Redis failure).
        // This has to stub `acquire(scope, key, fingerprint)`: after the atomic-acquire rewrite that is the
        // method createTrade calls, and an unstubbed mock returns null -- which the service then NPEs on,
        // surfacing as 500 instead of the 503 this test asserts.
        when(idempotencyServiceMock.acquire(anyString(), anyString(), anyString()))
            .thenThrow(new IdempotencyServiceUnavailableException(
                "tryAcquire",
                "idem-redis-down-001",
                new RedisConnectionFailureException("Connection refused")
            ));

        // When: create trade with Redis unavailable
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-redis-down-001");

        String requestBody = "{" +
            "\"tradeId\":\"trade-redis-down-001\"," +
            "\"buyerId\":\"buyer-redis-001\"," +
            "\"buyerNick\":\"TestBuyer\"," +
            "\"orderLines\":[{" +
            "\"skuId\":\"SKU_REDIS_001\"," +
            "\"productId\":\"PROD_REDIS_001\"," +
            "\"productName\":\"Test Product\"," +
            "\"shopId\":\"SHOP_REDIS_001\"," +
            "\"sellerId\":\"SELLER_REDIS_001\"," +
            "\"quantity\":1," +
            "\"priceCents\":5000" +
            "}]" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 503 Service Unavailable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("Idempotency service unavailable");
        assertThat(response.getBody()).contains("please retry later");
    }
}
