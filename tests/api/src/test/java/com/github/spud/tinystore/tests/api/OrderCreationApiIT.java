package com.github.spud.tinystore.tests.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order creation E2E API test
 * Tests the critical path: order creation through gateway
 */
class OrderCreationApiIT {

    private TestRestTemplate restTemplate;
    private String gatewayBaseUrl;

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    @Test
    void createOrder_throughGateway_shouldReturnCanonicalTradeProjection() {
        String tradeId = "trade-create-" + UUID.randomUUID();

        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("tradeId", tradeId);
        orderRequest.put("buyerId", "buyer-create-001");
        orderRequest.put("buyerNick", "buyer-create");
        orderRequest.put("addressId", "addr-001");
        orderRequest.put("traceId", "trace-" + tradeId);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(orderLine("SKU_A", "prod-1", "Product A", "SHOP_A", "seller-A", 1, 1000L, 0L));
        orderRequest.put("orderLines", orderLines);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "test-order-" + tradeId);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            gatewayBaseUrl + "/api/order/trades", 
            request,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> createData = (Map<String, Object>) response.getBody().get("data");
        assertThat(createData).isNotNull();
        assertThat(createData.get("tradeId")).isEqualTo(tradeId);
        assertThat(createData.get("paymentIntentId")).isInstanceOf(String.class);
        assertThat(((String) createData.get("paymentIntentId"))).isNotBlank();
        assertThat(((Number) createData.get("payableAmountCents")).longValue()).isGreaterThan(0L);

        ResponseEntity<Map> tradeDetailResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades/" + tradeId,
            HttpMethod.GET,
            null,
            Map.class
        );

        assertThat(tradeDetailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tradeDetailResponse.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> tradeDetail = (Map<String, Object>) tradeDetailResponse.getBody().get("data");
        assertThat(tradeDetail).isNotNull();
        assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
        assertThat(shopOrders).isNotEmpty();
        assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("PENDING_PAY");
        assertThat(shopOrders.get(0).get("inventoryStatus")).isEqualTo("PRE_DEDUCTED");
        assertThat(((Number) shopOrders.get(0).get("inventoryProjectionVersion")).intValue()).isEqualTo(2);
    }

    private Map<String, Object> orderLine(String skuId, String productId, String productName,
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
