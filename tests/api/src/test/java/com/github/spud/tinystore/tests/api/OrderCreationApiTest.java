package com.github.spud.tinystore.tests.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order creation E2E API test
 * Tests the critical path: order creation through gateway
 */
class OrderCreationApiTest {

    private TestRestTemplate restTemplate;
    private String gatewayBaseUrl;

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    @Test
    void createOrder_throughGateway_shouldReachOrderService() {
        // Given: a minimal order creation request
        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("userId", "test-user-001");
        orderRequest.put("shopId", "shop-001");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "test-order-" + System.currentTimeMillis());
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderRequest, headers);

        // When: creating order through gateway
        ResponseEntity<String> response = restTemplate.postForEntity(
            gatewayBaseUrl + "/api/order/trades", 
            request,
            String.class
        );

        // Then: request reaches order service
        // Note: we expect validation errors or success, but not routing errors
        assertThat(response.getStatusCode()).isIn(
            HttpStatus.OK, 
            HttpStatus.CREATED, 
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        
        // If we get 502/503/504, it means routing or service discovery failed
        assertThat(response.getStatusCode()).isNotIn(
            HttpStatus.BAD_GATEWAY,
            HttpStatus.SERVICE_UNAVAILABLE,
            HttpStatus.GATEWAY_TIMEOUT
        );
    }
}
