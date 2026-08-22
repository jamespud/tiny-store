package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
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
 * E2E: after-sale apply then merchant reject drives REFUND_ONLY case to REJECTED.
 */
class E2EAfterSaleRejectIT extends AbstractE2EBase {

    @Test
    void afterSaleRefundOnly_applyThenReject_shouldEndRejected() {
        String tradeId = UUID.randomUUID().toString();
        String caseId = "case-" + UUID.randomUUID();
        String orderId = createAndPaySingleOrder(tradeId);

        // Full fulfillment: accept -> ship -> delivered -> confirm-receipt -> SUCCESS.
        assertThat(post("/api/order/merchant/orders/" + orderId + "/accept",
            Map.of("traceId", "trace-accept-" + orderId), "idem-accept-" + orderId)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        String packageId = "pkg-" + UUID.randomUUID();
        Map<String, Object> ship = new HashMap<>();
        ship.put("packageId", packageId);
        ship.put("waybillNo", "WB" + System.nanoTime());
        ship.put("logistics", "SF-Express");
        ship.put("traceId", "trace-ship-" + orderId);
        assertThat(post("/api/order/merchant/orders/" + orderId + "/ship", ship,
            "idem-ship-" + orderId).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(post("/api/order/merchant/packages/" + packageId + "/delivered",
            Map.of("traceId", "trace-delivered-" + packageId), "idem-delivered-" + packageId)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(post("/api/order/trades/" + tradeId + "/confirm-receipt",
            Map.of("traceId", "trace-confirm-" + tradeId), "idem-confirm-" + tradeId)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> detail = getTradeDetail(tradeId);
            assertThat(shopOrdersOf(detail)).allSatisfy(o ->
                assertThat(o.get("orderStatus")).isEqualTo("SUCCESS"));
        });

        Map<String, Object> apply = new HashMap<>();
        apply.put("caseId", caseId);
        apply.put("tradeId", tradeId);
        apply.put("orderId", orderId);
        apply.put("afterSaleType", "REFUND_ONLY");
        apply.put("reason", "not as described");
        apply.put("traceId", "trace-apply-" + caseId);
        assertThat(post("/api/order/after-sale/cases", apply, "idem-apply-" + caseId)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(caseStatus(caseId)).isEqualTo("APPLIED"));

        Map<String, Object> reject = new HashMap<>();
        reject.put("rejectionReason", "insufficient evidence");
        reject.put("traceId", "trace-reject-" + caseId);
        assertThat(post("/api/order/after-sale/cases/" + caseId + "/reject", reject,
            "idem-reject-" + caseId).getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(caseStatus(caseId)).isEqualTo("REJECTED"));
    }

    private String createAndPaySingleOrder(String tradeId) {
        String buyerId = "buyer-as-" + UUID.randomUUID();
        Map<String, Object> create = new HashMap<>();
        create.put("tradeId", tradeId);
        create.put("buyerId", buyerId);
        create.put("buyerNick", "buyer-as");
        create.put("addressId", "addr-001");
        create.put("traceId", "trace-as-" + tradeId);
        create.put("orderLines", List.of(orderLine(SKU_A, PRODUCT_A, "Product A", SHOP_A, SELLER_A, 1, 1000L)));

        ResponseEntity<Map> createResp = post("/api/order/trades", create, "idem-as-" + tradeId);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createData = dataOf(createResp);
        String paymentIntentId = String.valueOf(createData.get("paymentIntentId"));
        long payable = ((Number) createData.get("payableAmountCents")).longValue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> pay = new HashMap<>();
            pay.put("paymentIntentId", paymentIntentId);
            pay.put("amountCents", payable);
            pay.put("traceId", "trace-pay-as-" + tradeId);
            assertThat(post("/api/order/trades/" + tradeId + "/pay/callback", pay,
                "idem-pay-as-" + tradeId + "-" + System.nanoTime())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        });

        Map<String, Object> detail = getTradeDetail(tradeId);
        return String.valueOf(shopOrdersOf(detail).get(0).get("orderId"));
    }

    private String caseStatus(String caseId) {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/order/after-sale/cases/" + caseId, Map.class);
        Map<String, Object> body = resp.getBody();
        if (body == null || body.get("data") == null) {
            return "";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return String.valueOf(data.get("caseStatus"));
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
