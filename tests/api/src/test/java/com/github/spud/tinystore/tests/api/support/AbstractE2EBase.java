package com.github.spud.tinystore.tests.api.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Shared base for black-box E2E ITs against the compose-test stack.
 *
 * <p>Applies the externalized E2E seed fixture once before each test class and exposes
 * the gateway client + common helpers.
 */
public abstract class AbstractE2EBase {

    protected static final String SHOP_A = "SHOP_A";
    protected static final String SHOP_B = "SHOP_B";
    protected static final String SKU_A = "SKU_A";
    protected static final String SKU_B = "SKU_B";
    protected static final String PRODUCT_A = "prod-1";
    protected static final String PRODUCT_B = "prod-2";
    protected static final String SELLER_A = "seller-A";
    protected static final String SELLER_B = "seller-B";

    protected TestRestTemplate restTemplate;
    protected String gatewayBaseUrl;

    @BeforeAll
    static void seedE2E() {
        SeedData.applyIfMissing();
    }

    @BeforeEach
    void setUpClient() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    protected Map<String, Object> orderLine(String skuId, String productId, String productName,
                                            String shopId, String sellerId, int quantity,
                                            long priceCents) {
        Map<String, Object> line = new HashMap<>();
        line.put("skuId", skuId);
        line.put("productId", productId);
        line.put("productName", productName);
        line.put("shopId", shopId);
        line.put("sellerId", sellerId);
        line.put("quantity", quantity);
        line.put("priceCents", priceCents);
        line.put("weightGrams", 0L);
        return line;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getTradeDetail(String tradeId) {
        var response = restTemplate.getForEntity(gatewayBaseUrl + "/api/order/trades/" + tradeId,
            Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            return Map.of();
        }
        Object data = body.get("data");
        return data instanceof Map ? (Map<String, Object>) data : Map.of();
    }

    protected List<Map<String, Object>> shopOrdersOf(Map<String, Object> tradeDetail) {
        Object orders = tradeDetail.get("shopOrders");
        if (orders instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) orders;
            return list;
        }
        return List.of();
    }
}
