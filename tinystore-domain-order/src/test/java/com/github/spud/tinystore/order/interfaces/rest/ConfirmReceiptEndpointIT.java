package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Confirm Receipt Endpoint Integration Test
 * 
 * Coverage:
 * - POST /api/order/trades/{tradeId}/confirm-receipt (确认收货)
 * 
 * Note: Uses @MockBean for TradeApplicationService to ensure endpoint wiring stability,
 * avoiding complex business state machine dependencies in this IT layer test.
 * Full business E2E coverage is provided by tests/api module.
 */
@DisplayName("Confirm Receipt Endpoint Integration Tests")
class ConfirmReceiptEndpointIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private TradeApplicationService tradeApplicationService;

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/confirm-receipt")
    @DisplayName("POST /api/order/trades/{tradeId}/confirm-receipt - valid request returns 200")
    void confirmReceipt_withMockedService_returns200() throws Exception {
        // Given: service confirms receipt successfully (mocked to avoid state machine complexity)
        doNothing().when(tradeApplicationService).confirmTradeReceipt(anyString(), anyString());

        // When: confirm receipt
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-confirm-receipt-it");

        String requestBody = "{}";

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/order/trades/trade-it-confirm/confirm-receipt",
            HttpMethod.POST,
            request,
            String.class
        );

        // Then: returns 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
