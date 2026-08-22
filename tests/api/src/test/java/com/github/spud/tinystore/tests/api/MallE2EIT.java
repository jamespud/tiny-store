package com.github.spud.tinystore.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import com.github.spud.tinystore.tests.api.support.SeedData;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Mall E2E Integration Test (Black-box, strict lifecycle closure)
 * 
 * Purpose: Validate full order lifecycle (create → pay → fulfill → confirm) through gateway
 * 
 * Migration strategy:
 * - Removed @TestMethodOrder and @Order dependencies (avoid cross-method shared state)
 * - Single-method scenario test for strict closure
 * - Seed contract validation before business closure
 *
 * Assumptions:
 * - Compose test stack is running (14 containers: infra + 8 services)
 * - Gateway accessible at ${gateway.base.url} (default http://localhost:8080)
 * - Security disabled in test profile
 * - Fixed seeds (SKU_A/SKU_B in SHOP_A/SHOP_B) are available and validated
 * 
 * Endpoint Coverage Tags:
 * - Each @Tag("ep:...") marks which service endpoint is exercised
 * - Format: ep:<service>:<METHOD>:<canonicalPath>
 * - These tags are validated via EndpointCoverageContractTest
 */
class MallE2EIT {

    @BeforeAll
    static void seedE2E() {
        // Seed fixture externalized out of the core Flyway chain (see demo-to-production E4).
        SeedData.applyIfMissing();
    }

    private TestRestTemplate restTemplate;
    private String gatewayBaseUrl;

    // Fixed seed data (contract with docker-compose-test init)
    private static final String SHOP_A = "SHOP_A";
    private static final String SHOP_B = "SHOP_B";
    private static final String SKU_A = "SKU_A";
    private static final String SKU_B = "SKU_B";

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        restTemplate = new TestRestTemplate();
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:product:GET:/api/skus/{skuId}")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/reserve")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/release")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/quote")
    @org.junit.jupiter.api.Tag("ep:promotion:POST:/api/promotion/checkout/release")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/accept")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/orders/{orderId}/ship")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/merchant/packages/{packageId}/delivered")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/confirm-receipt")
    void strictClosureE2E_createTradeToConfirmReceipt_shouldCompleteFullLifecycle() {
        // === Seed Contract Validation (fail-fast if environment not ready) ===
        validateSeedContract();

        // === Business Closure: Create Trade → Pay → Fulfill → Confirm Receipt ===
        String tradeId = UUID.randomUUID().toString();
        String buyerId = "buyer-e2e-" + UUID.randomUUID();
        String paymentIntentId;
        long payableAmountCents;

        // Step 1: Create trade
        Map<String, Object> createTradeRequest = new HashMap<>();
        createTradeRequest.put("tradeId", tradeId);
        createTradeRequest.put("buyerId", buyerId);
        createTradeRequest.put("buyerNick", "buyer-strict");
        createTradeRequest.put("addressId", "addr-001");
        createTradeRequest.put("traceId", "trace-" + tradeId);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        orderLines.add(orderLine(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 2, 1000L, 0L));
        // Single-order scenario for strict lifecycle closure (create → pay → fulfill → confirm)
        createTradeRequest.put("orderLines", orderLines);

        HttpHeaders createHeaders = new HttpHeaders();
        createHeaders.add("Content-Type", "application/json");
        createHeaders.add("Idempotency-Key", "idem-" + tradeId);
        createHeaders.add("X-Trace-ID", "strictClosureE2E_createTradeToConfirmReceipt_shouldCompleteFullLifecycle");

        ResponseEntity<Map> createResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(createTradeRequest, createHeaders),
            Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createBody = createResponse.getBody();
        assertThat(createBody).isNotNull();
        assertThat(createBody.get("code")).isEqualTo(0);

        Map<String, Object> createData = (Map<String, Object>) createBody.get("data");
        assertThat(createData).isNotNull();
        paymentIntentId = (String) createData.get("paymentIntentId");
        payableAmountCents = ((Number) createData.get("payableAmountCents")).longValue();
        assertThat(paymentIntentId).isNotBlank();
        assertThat(payableAmountCents).isGreaterThan(0);

        // Step 2: Verify trade initial state (UNPAID, shopOrders PENDING_PAY)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("PENDING_PAY");
        });

        // Step 3: Pay callback (simulate external payment success)
        Map<String, Object> payCallbackRequest = new HashMap<>();
        payCallbackRequest.put("paymentIntentId", paymentIntentId);
        payCallbackRequest.put("amountCents", payableAmountCents);
        payCallbackRequest.put("traceId", "trace-pay-" + tradeId);

        HttpHeaders payHeaders = new HttpHeaders();
        payHeaders.add("Content-Type", "application/json");
        payHeaders.add("Idempotency-Key", "idem-pay-" + tradeId);

        // Async promotion commit gate: 下单后 trade 为 PENDING（不可支付），支付回调会 409
        // (TRADE_NOT_READY_FOR_PAYMENT, 可重试)；轮询直至 COMMITTED 后回调 200。
        // 注意：gateway 幂等会把 409 响应缓存为已完成——每次重试必须用新的 Idempotency-Key，
        // 否则同一 key 的后续请求在 gateway 层直接 409。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpHeaders retryPayHeaders = new HttpHeaders();
            retryPayHeaders.add("Content-Type", "application/json");
            retryPayHeaders.add("Idempotency-Key", "idem-pay-" + tradeId + "-" + System.nanoTime());
            ResponseEntity<Map> payResponse = restTemplate.exchange(
                gatewayBaseUrl + "/api/order/trades/" + tradeId + "/pay/callback",
                HttpMethod.POST,
                new HttpEntity<>(payCallbackRequest, retryPayHeaders),
                Map.class
            );
            assertThat(payResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

        // Step 4: Verify trade paid state (PAID, shopOrders PENDING_SHIP)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("PAID");
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("PENDING_SHIP");
        });

        // Step 5: Merchant accept order (optional but recommended for state clarity)
        Map<String, Object> tradeDetail = getTradeDetail(tradeId);
        List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
        String firstOrderId = (String) shopOrders.get(0).get("orderId");

        Map<String, Object> acceptRequest = new HashMap<>();
        acceptRequest.put("traceId", "trace-accept-" + tradeId);

        HttpHeaders acceptHeaders = new HttpHeaders();
        acceptHeaders.add("Content-Type", "application/json");
        acceptHeaders.add("Idempotency-Key", "idem-accept-" + firstOrderId);

        ResponseEntity<Map> acceptResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/merchant/orders/" + firstOrderId + "/accept",
            HttpMethod.POST,
            new HttpEntity<>(acceptRequest, acceptHeaders),
            Map.class
        );

        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 6: Merchant ship order
        String packageId = "pkg-" + UUID.randomUUID();
        Map<String, Object> shipRequest = new HashMap<>();
        shipRequest.put("packageId", packageId);
        shipRequest.put("waybillNo", "WB" + System.currentTimeMillis());
        shipRequest.put("logistics", "SF-Express");
        shipRequest.put("traceId", "trace-ship-" + tradeId);

        HttpHeaders shipHeaders = new HttpHeaders();
        shipHeaders.add("Content-Type", "application/json");
        shipHeaders.add("Idempotency-Key", "idem-ship-" + firstOrderId);

        ResponseEntity<Map> shipResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/merchant/orders/" + firstOrderId + "/ship",
            HttpMethod.POST,
            new HttpEntity<>(shipRequest, shipHeaders),
            Map.class
        );

        assertThat(shipResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 7: Verify order shipped (PENDING_RECEIVE, packages exist)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> detail = getTradeDetail(tradeId);
            List<Map<String, Object>> orders = (List<Map<String, Object>>) detail.get("shopOrders");
            
            // Find the specific order that was shipped (by orderId, not by index)
            Map<String, Object> shippedOrder = orders.stream()
                .filter(order -> firstOrderId.equals(order.get("orderId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Order not found: " + firstOrderId));
            
            assertThat(shippedOrder.get("orderStatus")).isEqualTo("PENDING_RECEIVE");
            List<Map<String, Object>> packages = (List<Map<String, Object>>) shippedOrder.get("packages");
            assertThat(packages).isNotEmpty();
            assertThat(packages.get(0).get("packageId")).isEqualTo(packageId);
        });

        // Step 8: Package delivered
        Map<String, Object> deliveredRequest = new HashMap<>();
        deliveredRequest.put("traceId", "trace-delivered-" + tradeId);

        HttpHeaders deliveredHeaders = new HttpHeaders();
        deliveredHeaders.add("Content-Type", "application/json");
        deliveredHeaders.add("Idempotency-Key", "idem-delivered-" + packageId);

        ResponseEntity<Map> deliveredResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/merchant/packages/" + packageId + "/delivered",
            HttpMethod.POST,
            new HttpEntity<>(deliveredRequest, deliveredHeaders),
            Map.class
        );

        assertThat(deliveredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 9: Confirm receipt (user action)
        Map<String, Object> confirmRequest = new HashMap<>();
        confirmRequest.put("traceId", "trace-confirm-" + tradeId);

        HttpHeaders confirmHeaders = new HttpHeaders();
        confirmHeaders.add("Content-Type", "application/json");
        confirmHeaders.add("Idempotency-Key", "idem-confirm-" + tradeId);

        ResponseEntity<Map> confirmResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades/" + tradeId + "/confirm-receipt",
            HttpMethod.POST,
            new HttpEntity<>(confirmRequest, confirmHeaders),
            Map.class
        );

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 10: Verify final state (shopOrders SUCCESS)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> detail = getTradeDetail(tradeId);
            List<Map<String, Object>> orders = (List<Map<String, Object>>) detail.get("shopOrders");
            for (Map<String, Object> order : orders) {
                assertThat(order.get("orderStatus")).isEqualTo("SUCCESS");
            }
        });
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/cancel")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    void cancelledCanonicalTrade_payCallbackShouldConflictAndKeepTradeUnpaid() {
        validateSeedContract();

        String tradeId = UUID.randomUUID().toString();
        String buyerId = "buyer-conflict-" + UUID.randomUUID();

        Map<String, Object> createTradeRequest = new HashMap<>();
        createTradeRequest.put("tradeId", tradeId);
        createTradeRequest.put("buyerId", buyerId);
        createTradeRequest.put("buyerNick", "buyer-conflict");
        createTradeRequest.put("addressId", "addr-001");
        createTradeRequest.put("traceId", "trace-" + tradeId);
        createTradeRequest.put("orderLines", List.of(orderLine(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 1, 1000L, 0L)));

        HttpHeaders createHeaders = new HttpHeaders();
        createHeaders.add("Content-Type", "application/json");
        createHeaders.add("Idempotency-Key", "idem-conflict-" + tradeId);

        ResponseEntity<Map> createResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(createTradeRequest, createHeaders),
            Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> createData = (Map<String, Object>) createResponse.getBody().get("data");
        assertThat(createData).isNotNull();
        String paymentIntentId = (String) createData.get("paymentIntentId");
        long payableAmountCents = ((Number) createData.get("payableAmountCents")).longValue();

        Map<String, Object> cancelRequest = new HashMap<>();
        cancelRequest.put("reason", "buyer-cancelled-before-payment");

        HttpHeaders cancelHeaders = new HttpHeaders();
        cancelHeaders.add("Content-Type", "application/json");
        cancelHeaders.add("Idempotency-Key", "idem-cancel-" + tradeId);

        ResponseEntity<Map> cancelResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades/" + tradeId + "/cancel",
            HttpMethod.POST,
            new HttpEntity<>(cancelRequest, cancelHeaders),
            Map.class
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("CLOSED");
        });

        Map<String, Object> payCallbackRequest = new HashMap<>();
        payCallbackRequest.put("paymentIntentId", paymentIntentId);
        payCallbackRequest.put("amountCents", payableAmountCents);
        payCallbackRequest.put("traceId", "trace-pay-conflict-" + tradeId);

        HttpHeaders payHeaders = new HttpHeaders();
        payHeaders.add("Content-Type", "application/json");
        payHeaders.add("Idempotency-Key", "idem-pay-conflict-" + tradeId);

        ResponseEntity<Map> payResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades/" + tradeId + "/pay/callback",
            HttpMethod.POST,
            new HttpEntity<>(payCallbackRequest, payHeaders),
            Map.class
        );

        assertThat(payResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("CLOSED");
        });
    }

    @Test
    @org.junit.jupiter.api.Disabled("async promotion-commit mode: reservation-expiry rejection semantics are "
        + "superseded by the promotion commit gate (trade reaches COMMITTED within seconds); "
        + "equivalent terminal-rejection coverage lives in TradeApplicationServiceAutoCancelTest "
        + "and PaymentGateTest (TRADE_TERMINAL), and cancelledCanonicalTrade E2E")
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/reservations/confirm")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades")
    @org.junit.jupiter.api.Tag("ep:order:GET:/api/order/trades/{tradeId}")
    @org.junit.jupiter.api.Tag("ep:order:POST:/api/order/trades/{tradeId}/pay/callback")
    void expiredCanonicalTrade_payCallbackShouldConflictAndKeepTradeUnpaid() {
        validateSeedContract();

        String tradeId = UUID.randomUUID().toString();
        String buyerId = "buyer-expired-" + UUID.randomUUID();

        Map<String, Object> createTradeRequest = new HashMap<>();
        createTradeRequest.put("tradeId", tradeId);
        createTradeRequest.put("buyerId", buyerId);
        createTradeRequest.put("buyerNick", "buyer-expired");
        createTradeRequest.put("addressId", "addr-001");
        createTradeRequest.put("traceId", "trace-" + tradeId);
        createTradeRequest.put("orderLines", List.of(orderLine(SKU_A, "prod-1", "Product A", SHOP_A, "seller-A", 1, 1000L, 0L)));

        HttpHeaders createHeaders = new HttpHeaders();
        createHeaders.add("Content-Type", "application/json");
        createHeaders.add("Idempotency-Key", "idem-expired-" + tradeId);

        ResponseEntity<Map> createResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades",
            HttpMethod.POST,
            new HttpEntity<>(createTradeRequest, createHeaders),
            Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> createData = (Map<String, Object>) createResponse.getBody().get("data");
        assertThat(createData).isNotNull();
        String paymentIntentId = (String) createData.get("paymentIntentId");
        long payableAmountCents = ((Number) createData.get("payableAmountCents")).longValue();

        await().pollDelay(Duration.ofSeconds(75)).atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("PENDING_PAY");
        });

        Map<String, Object> payCallbackRequest = new HashMap<>();
        payCallbackRequest.put("paymentIntentId", paymentIntentId);
        payCallbackRequest.put("amountCents", payableAmountCents);
        payCallbackRequest.put("traceId", "trace-pay-expired-" + tradeId);

        HttpHeaders payHeaders = new HttpHeaders();
        payHeaders.add("Content-Type", "application/json");
        payHeaders.add("Idempotency-Key", "idem-pay-expired-" + tradeId);

        ResponseEntity<Map> payResponse = restTemplate.exchange(
            gatewayBaseUrl + "/api/order/trades/" + tradeId + "/pay/callback",
            HttpMethod.POST,
            new HttpEntity<>(payCallbackRequest, payHeaders),
            Map.class
        );

        assertThat(payResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Map<String, Object> tradeDetail = getTradeDetail(tradeId);
            assertThat(tradeDetail.get("payStatus")).isEqualTo("UNPAID");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shopOrders = (List<Map<String, Object>>) tradeDetail.get("shopOrders");
            assertThat(shopOrders).isNotEmpty();
            assertThat(shopOrders.get(0).get("orderStatus")).isEqualTo("PENDING_PAY");
        });
    }

    /**
     * Seed contract validation (fail-fast with clear error if seeds not ready)
     */
    private void validateSeedContract() {
        // 1. Validate Product seeds (SKU_A/SKU_B exist via direct service port)
        validateSkuExists(SKU_A, SHOP_A);
        validateSkuExists(SKU_B, SHOP_B);

        // 2. Validate Inventory (canonical reserve + release to check stock availability)
        validateInventoryAvailable();

        // 3. Validate Promotion (quote + release to check promotion can quote)
        validatePromotionCanQuote();
    }

    private void validateSkuExists(String skuId, String shopId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Shop-Id", shopId);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:8090/api/skus/" + skuId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        assertThat(response.getStatusCode())
            .withFailMessage("Seed contract failed: SKU %s not found in shop %s (status: %s)", 
                skuId, shopId, response.getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    private void validateInventoryAvailable() {
        AtomicReference<List<Map<String, Object>>> occupyPairsRef = new AtomicReference<>();
        AtomicReference<String> releaseOrderIdRef = new AtomicReference<>();
        AtomicReference<String> releaseIdempotencyKeyRef = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .ignoreExceptions()
            .untilAsserted(() -> {
                if (occupyPairsRef.get() == null) {
                    String tradeId = "seed-check-" + UUID.randomUUID();
                    String orderId = "seed-order-" + UUID.randomUUID();

                    Map<String, Object> reserveRequest = new HashMap<>();
                    reserveRequest.put("tradeId", tradeId);
                    reserveRequest.put("orderId", orderId);
                    List<Map<String, Object>> reserveItems = new ArrayList<>();
                    Map<String, Object> reserveItem = new HashMap<>();
                    reserveItem.put("shopId", SHOP_A);
                    reserveItem.put("skuId", SKU_A);
                    reserveItem.put("quantity", 1);
                    reserveItems.add(reserveItem);
                    reserveRequest.put("items", reserveItems);

                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Content-Type", "application/json");
                    headers.add("Idempotency-Key", "seed-inv-" + UUID.randomUUID());

                    ResponseEntity<Map> response = restTemplate.exchange(
                        "http://localhost:13000/api/inventory/reservations/reserve",
                        HttpMethod.POST,
                        new HttpEntity<>(reserveRequest, headers),
                        Map.class
                    );

                    assertThat(response.getStatusCode())
                        .withFailMessage("Seed contract failed: Inventory reserve HTTP status %s, body=%s",
                            response.getStatusCode(), response.getBody())
                        .isEqualTo(HttpStatus.OK);

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("success"))
                        .withFailMessage("Seed contract failed: Inventory reserve failed, body=%s", body)
                        .isEqualTo(true);

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> occupyPairs = (List<Map<String, Object>>) body.get("occupyPairs");
                    assertThat(occupyPairs)
                        .withFailMessage("Seed contract failed: Inventory reserve returned no occupyPairs, body=%s", body)
                        .isNotNull()
                        .isNotEmpty();
                    occupyPairsRef.set(occupyPairs);
                    releaseOrderIdRef.set(orderId);
                    releaseIdempotencyKeyRef.set("seed-inv-rel-" + UUID.randomUUID());
                }

                Map<String, Object> releaseRequest = new HashMap<>();
                releaseRequest.put("orderId", releaseOrderIdRef.get());
                releaseRequest.put("reason", "seed-check-cleanup");
                releaseRequest.put("occupyPairs", occupyPairsRef.get());

                HttpHeaders releaseHeaders = new HttpHeaders();
                releaseHeaders.add("Content-Type", "application/json");
                releaseHeaders.add("Idempotency-Key", releaseIdempotencyKeyRef.get());

                ResponseEntity<Map> releaseResponse = restTemplate.exchange(
                    "http://localhost:13000/api/inventory/reservations/release",
                    HttpMethod.POST,
                    new HttpEntity<>(releaseRequest, releaseHeaders),
                    Map.class
                );

                assertThat(releaseResponse.getStatusCode())
                    .withFailMessage("Seed contract failed: Inventory release HTTP status %s, body=%s",
                        releaseResponse.getStatusCode(), releaseResponse.getBody())
                    .isEqualTo(HttpStatus.OK);
                assertThat(releaseResponse.getBody())
                    .withFailMessage("Seed contract failed: Inventory release response body is null")
                    .isNotNull();
                assertThat(releaseResponse.getBody().get("success"))
                    .withFailMessage("Seed contract failed: Inventory release failed, body=%s", releaseResponse.getBody())
                    .isEqualTo(true);
            });
    }


    private void validatePromotionCanQuote() {
        Map<String, Object> quoteRequest = new HashMap<>();
        quoteRequest.put("userId", "seed-check-user");
        quoteRequest.put("traceId", "seed-quote-" + UUID.randomUUID());
        quoteRequest.put("addressId", "addr-001");

        List<Map<String, Object>> lines = new ArrayList<>();
        Map<String, Object> line = new HashMap<>();
        line.put("skuId", SKU_A);
        line.put("shopId", SHOP_A);
        line.put("quantity", 1);
        line.put("baseUnitPriceCents", 1000L);
        line.put("weightGrams", 0L);
        lines.add(line);
        quoteRequest.put("lines", lines);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Idempotency-Key", "seed-promo-" + UUID.randomUUID());

        ResponseEntity<Map> response = restTemplate.exchange(
            "http://localhost:1200/api/promotion/checkout/quote",
            HttpMethod.POST,
            new HttpEntity<>(quoteRequest, headers),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        String status = (String) body.get("status");
        assertThat(status)
            .withFailMessage("Seed contract failed: Promotion quote status is %s (expected OK or OK_WITH_CHANGE)", status)
            .isIn("OK", "OK_WITH_CHANGE");

        String quoteId = (String) body.get("quoteId");
        assertThat(quoteId)
            .withFailMessage("Seed contract failed: Promotion quoteId is null")
            .isNotNull();

        // Release the quote
        Map<String, Object> releaseRequest = new HashMap<>();
        releaseRequest.put("quoteId", quoteId);
        releaseRequest.put("tradeId", quoteRequest.get("traceId"));
        releaseRequest.put("reason", "seed-check-cleanup");

        HttpHeaders releaseHeaders = new HttpHeaders();
        releaseHeaders.add("Content-Type", "application/json");
        releaseHeaders.add("Idempotency-Key", "seed-promo-rel-" + UUID.randomUUID());

        restTemplate.exchange(
            "http://localhost:1200/api/promotion/checkout/release",
            HttpMethod.POST,
            new HttpEntity<>(releaseRequest, releaseHeaders),
            Map.class
        );
    }

    private Map<String, Object> getTradeDetail(String tradeId) {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            gatewayBaseUrl + "/api/order/trades/" + tradeId,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);

        return (Map<String, Object>) body.get("data");
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
