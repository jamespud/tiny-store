package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.github.spud.tinystore.tests.api.support.AbstractE2EBase;

/**
 * E2E: multi-shop trade is split into per-shop shop orders and each finishes independently.
 */
class E2EMultiShopFlowIT extends AbstractE2EBase {

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/accept")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/confirm-receipt")
    void multiShopTrade_shouldSplitIntoTwoShopOrdersAndBothFinish() {
        String tradeId = UUID.randomUUID().toString();
        String buyerId = "buyer-multi-" + UUID.randomUUID();

        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("tradeId", tradeId);
        createRequest.put("buyerId", buyerId);
        createRequest.put("buyerNick", "buyer-multi");
        createRequest.put("addressId", "addr-001");
        createRequest.put("traceId", "trace-multi-" + tradeId);

        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(orderLine(SKU_A, PRODUCT_A, "Product A", SHOP_A, SELLER_A, 1, 1000L));
        lines.add(orderLine(SKU_B, PRODUCT_B, "Product B", SHOP_B, SELLER_B, 1, 2000L));
        createRequest.put("orderLines", lines);

        ResponseEntity<Map> create = post("/api/order/trades", createRequest, "idem-multi-" + tradeId);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createData = dataOf(create);
        String paymentIntentId = String.valueOf(createData.get("paymentIntentId"));
        long payable = ((Number) createData.get("payableAmountCents")).longValue();
        assertThat(payable).isEqualTo(3000L); // 1000 + 2000 (no promo)

        // Trade is split into 2 shop orders.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> detail = getTradeDetail(tradeId);
            assertThat(shopOrdersOf(detail)).hasSize(2);
        });

        // Pay (retry through async promotion-commit gate with fresh idempotency keys).
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> payReq = new HashMap<>();
            payReq.put("paymentIntentId", paymentIntentId);
            payReq.put("amountCents", payable);
            payReq.put("traceId", "trace-pay-multi-" + tradeId);
            ResponseEntity<Map> pay = post("/api/order/trades/" + tradeId + "/pay/callback",
                payReq, "idem-pay-multi-" + tradeId + "-" + System.nanoTime());
            assertThat(pay.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

        Map<String, Object> detail = getTradeDetail(tradeId);
        List<Map<String, Object>> shopOrders = shopOrdersOf(detail);
        assertThat(shopOrders).hasSize(2);
        assertThat(shopOrders).allSatisfy(o ->
            assertThat(o.get("orderStatus")).isEqualTo("PENDING_SHIP"));

        // Each shop order ships (accept -> ship), then buyer confirms receipt on the trade.
        for (Map<String, Object> order : shopOrders) {
            String orderId = String.valueOf(order.get("orderId"));
            assertThat(post("/api/order/merchant/orders/" + orderId + "/accept",
                Map.of("traceId", "trace-accept-" + orderId),
                "idem-accept-" + orderId).getStatusCode()).isEqualTo(HttpStatus.OK);

            String packageId = "pkg-" + UUID.randomUUID();
            Map<String, Object> ship = new HashMap<>();
            ship.put("packageId", packageId);
            ship.put("waybillNo", "WB" + System.nanoTime());
            ship.put("logistics", "SF-Express");
            ship.put("traceId", "trace-ship-" + orderId);
            assertThat(post("/api/order/merchant/orders/" + orderId + "/ship", ship,
                "idem-ship-" + orderId).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(post("/api/order/trades/" + tradeId + "/confirm-receipt",
            Map.of("traceId", "trace-confirm-" + tradeId),
            "idem-confirm-" + tradeId).getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> finalDetail = getTradeDetail(tradeId);
            assertThat(shopOrdersOf(finalDetail)).allSatisfy(o ->
                assertThat(o.get("orderStatus")).isEqualTo("SUCCESS"));
        });
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body, String idemKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Idempotency-Key", idemKey);
        return restTemplate.exchange(gatewayBaseUrl + path, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<Map> response) {
        Map<String, Object> body = response.getBody();
        return body == null ? Map.of() : (Map<String, Object>) body.get("data");
    }
}
