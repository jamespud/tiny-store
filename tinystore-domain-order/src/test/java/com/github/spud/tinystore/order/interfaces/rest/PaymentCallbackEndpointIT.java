package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
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
 * Payment Callback Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/order/trades/{tradeId}/pay/callback (支付成功回调)
 * 
 * Tests payment callback handling with database
 */
@DisplayName("Payment Callback Endpoint Integration Tests")
class PaymentCallbackEndpointIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeJpaRepository tradeRepository;

    @Autowired
    private PaymentIntentJpaRepository paymentIntentRepository;

    @BeforeEach
    void cleanup() {
        paymentIntentRepository.deleteAll();
        outboxEventJpaRepository.deleteAll();
        tradeRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    @DisplayName("POST /api/order/trades/{tradeId}/pay/callback - processes payment success")
    void paymentCallback_success_returns200() {
        // Given: trade in UNPAID status
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-pay")
            .buyerId("user-pay")
            .payStatus("UNPAID")
            .totalAmountCents(29900L)
            .payableAmountCents(29900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // Given: payment intent exists
        PaymentIntentEntity paymentIntent = PaymentIntentEntity.builder()
            .paymentId("payment-intent-001")
            .tradeId("trade-pay")
            .amountCents(29900L)
            .status("PENDING")
            .buyerId("user-pay")
            .createdAt(LocalDateTime.now())
            .build();
        paymentIntentRepository.save(paymentIntent);

        // When: payment callback
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-pay-callback-001");

        String requestBody = "{" +
            "\"paymentIntentId\":\"payment-intent-001\"," +
            "\"amountCents\":29900," +
            "\"traceId\":\"trace-pay-001\"" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades/trade-pay/pay/callback",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    @DisplayName("POST /api/order/trades/{tradeId}/pay/callback - missing paymentIntentId returns 500")
    void paymentCallback_missingPaymentIntentId_returns500() {
        // Given: trade exists
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-pay-2")
            .buyerId("user-pay-2")
            .payStatus("UNPAID")
            .totalAmountCents(19900L)
            .payableAmountCents(19900L)
            .discountAmountCents(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        tradeRepository.save(trade);

        // When: payment callback without paymentIntentId
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-pay-callback-002");

        String requestBody = "{" +
            "\"amountCents\":19900" +
            "}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades/trade-pay-2/pay/callback",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 500
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
