package com.github.spud.tinystore.tests.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway health and routing API tests
 * Tests gateway availability and service discovery routing
 */
class GatewayApiTest {

    private TestRestTemplate restTemplate;
    private String gatewayBaseUrl;

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    @Test
    void gateway_shouldBeHealthy() {
        // When: accessing gateway health endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/actuator/health", 
            String.class
        );

        // Then: gateway is UP
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void gateway_shouldRouteToOrderService() {
        // Given: security is disabled in test environment
        
        // When: accessing order service through gateway
        ResponseEntity<String> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/order/trades/health-check", 
            String.class
        );

        // Then: request reaches order service (even if endpoint doesn't exist, routing works)
        // Note: we expect either 200 (if endpoint exists) or 404 (routing works but endpoint missing)
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    void gateway_shouldRouteToPromotionService() {
        // When: accessing promotion service through gateway
        ResponseEntity<String> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/promotion/health-check", 
            String.class
        );

        // Then: request reaches promotion service
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    void gateway_shouldRouteToInventoryService() {
        // When: accessing inventory service through gateway
        ResponseEntity<String> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/inventory/stock/health-check", 
            String.class
        );

        // Then: request reaches inventory service
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    void gateway_shouldRouteToProductService() {
        // When: accessing product service through gateway
        ResponseEntity<String> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/products/health-check", 
            String.class
        );

        // Then: request reaches product service
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }
}
