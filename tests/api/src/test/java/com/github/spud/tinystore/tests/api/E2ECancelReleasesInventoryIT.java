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
import com.github.spud.tinystore.tests.api.support.E2ePostgres;

/**
 * E2E: cancelling an unpaid trade releases the PRE_DEDUCTED reservation back to RELEASED.
 */
class E2ECancelReleasesInventoryIT extends AbstractE2EBase {

    @Test
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/cancel")
    void cancelUnpaidTrade_shouldReleasePreDeductedReservation() {
        String tradeId = UUID.randomUUID().toString();
        createTrade(tradeId);

        // Reservation is PRE_DEDUCTED shortly after create (async INVENTORY_RESERVE_DB via outbox).
        try (E2ePostgres pg = new E2ePostgres()) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(pg.countReservationsByTradeAndStatus(tradeId, "PRE_DEDUCTED")).isEqualTo(1L));
        }

        Map<String, Object> cancel = new HashMap<>();
        cancel.put("reason", "buyer-changed-mind");
        ResponseEntity<Map> cancelResp = post("/api/order/trades/" + tradeId + "/cancel",
            cancel, "idem-cancel-rel-" + tradeId);
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> detail = getTradeDetail(tradeId);
            assertThat(detail.get("payStatus")).isEqualTo("UNPAID");
            assertThat(shopOrdersOf(detail)).allSatisfy(o ->
                assertThat(o.get("orderStatus")).isEqualTo("CLOSED"));
        });

        // Strong DB assertion: the reservation moved PRE_DEDUCTED -> RELEASED (async release).
        try (E2ePostgres pg = new E2ePostgres()) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(pg.countReservationsByTradeAndStatus(tradeId, "RELEASED")).isEqualTo(1L);
                assertThat(pg.countReservationsByTradeAndStatus(tradeId, "PRE_DEDUCTED")).isZero();
            });
        }
    }

    private void createTrade(String tradeId) {
        Map<String, Object> create = new HashMap<>();
        create.put("tradeId", tradeId);
        create.put("buyerId", "buyer-cancel-" + UUID.randomUUID());
        create.put("buyerNick", "buyer-cancel");
        create.put("addressId", "addr-001");
        create.put("traceId", "trace-cancel-" + tradeId);
        create.put("orderLines", List.of(orderLine(SKU_A, PRODUCT_A, "Product A", SHOP_A, SELLER_A, 1, 1000L)));
        assertThat(post("/api/order/trades", create, "idem-cancel-" + tradeId).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body, String idemKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Idempotency-Key", idemKey);
        return restTemplate.exchange(gatewayBaseUrl + path, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class);
    }
}
