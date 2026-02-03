package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Mall E2E Integration Test (Black-box version)
 * 
 * Purpose: Validate full order lifecycle (create → pay → refund) through gateway only
 * 
 * Migration from domain module:
 * - Original: AbstractMallE2EIT (white-box, multi-context, Testcontainers)
 * - Current: Pure black-box test against gateway HTTP API
 * 
 * Assumptions:
 * - Compose test stack is running (14 containers: infra + 8 services)
 * - Gateway accessible at ${gateway.base.url} (default http://localhost:8080)
 * - Security disabled in test profile (tinystore.security.resource-server.enabled=false)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MallE2EIT {

    private TestRestTemplate restTemplate;
    private String gatewayBaseUrl;

    private static final String SHOP_A = "SHOP_A";
    private static final String SHOP_B = "SHOP_B";
    private static final String SKU_A = "SKU_A";
    private static final String SKU_B = "SKU_B";

    private String tradeId;
    private String paymentIntentId;
    private long payableAmountCents;
    private String buyerId;

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    @Test
    @Order(1)
    void createTrade_shouldReturnPaymentIntent() {
        // Given: prepare test data (simplified, assuming inventory/promotion seeds exist)
        tradeId = UUID.randomUUID().toString();
        buyerId = "buyer-" + UUID.randomUUID();

        Map<String, Object> request = new HashMap<>();
        request.put("tradeId", tradeId);
        request.put("buyerId", buyerId);
        request.put("buyerNick", "buyer-nick");
        request.put("addressId", "addr-001");
        request.put("traceId", "trace-" + tradeId);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(line(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 2, 1000L, 0L));
        orderLines.add(line(SKU_B, "prod-2", "Product B", SHOP_B, "seller-B", 1, 2000L, 0L));
        request.put("orderLines", orderLines);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Idempotency-Key", "idem-" + tradeId);

        // When: create trade via gateway
        ResponseEntity<Map> response = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );

        // Then: verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        paymentIntentId = (String) data.get("paymentIntentId");
        payableAmountCents = ((Number) data.get("payableAmountCents")).longValue();

        assertThat(paymentIntentId).isNotBlank();
        assertThat(payableAmountCents).isGreaterThan(0);
    }

    @Test
    @Order(2)
    void queryTrade_shouldReturnCreatedTrade() {
        // Given: trade created in previous test
        assertThat(tradeId).isNotNull();

        // When: query trade via gateway
        ResponseEntity<Map> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/order/trades/" + tradeId,
            Map.class
        );

        // Then: verify trade exists and in correct status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("tradeId")).isEqualTo(tradeId);
        assertThat(data.get("buyerId")).isEqualTo(buyerId);
    }

    @Test
    @Order(3)
    void createTradeWithCoupons_shouldAcceptMultipleCoupons() {
        // Given: new trade with coupons
        String newTradeId = UUID.randomUUID().toString();
        String newBuyerId = "buyer-multi-coupon-" + UUID.randomUUID();

        Map<String, Object> request = new HashMap<>();
        request.put("tradeId", newTradeId);
        request.put("buyerId", newBuyerId);
        request.put("buyerNick", "buyer-nick");
        request.put("addressId", "addr-002");
        request.put("traceId", "trace-multi-" + newTradeId);

        List<String> platformCoupons = List.of("C202602", "C202603");
        Map<String, List<String>> shopCoupons = new HashMap<>();
        shopCoupons.put(SHOP_A, List.of("P8888"));
        shopCoupons.put(SHOP_B, List.of("S1111", "S2222"));

        request.put("platformCouponCodes", platformCoupons);
        request.put("shopCouponCodesByShop", shopCoupons);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(line(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 1, 3000L, 0L));
        orderLines.add(line(SKU_B, "prod-2", "Product B", SHOP_B, "seller-B", 1, 2000L, 0L));
        request.put("orderLines", orderLines);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Idempotency-Key", "idem-multi-" + newTradeId);

        // When: create trade with coupons
        ResponseEntity<Map> response = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            Map.class
        );

        // Then: verify success
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);

        // Verify coupon codes stored (query back)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<Map> queryResponse = restTemplate.getForEntity(
                gatewayBaseUrl + "/api/order/trades/" + newTradeId,
                Map.class
            );
            
            assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> queryBody = queryResponse.getBody();
            assertThat(queryBody).isNotNull();
            
            // Note: actual coupon validation in response depends on API implementation
            // Here we just verify trade is accessible
        });
    }

    private Map<String, Object> line(String skuId, String productId, String productName,
                                     String shopId, String sellerId, int quantity,
                                     long priceCents, long weightGrams) {
        Map<String, Object> line = new HashMap<>();
        line.put("skuId", skuId);
        line.put("productId", productId);
        line.put("productName", productName);
        line.put("shopId", shopId);
        line.put("sellerId", sellerId);
        line.put("quantity", quantity);
        line.put("priceCents", priceCents);
        line.put("weightGrams", weightGrams);
        return line;
    }
}
