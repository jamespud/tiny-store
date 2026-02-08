package com.github.spud.tinystore.tests.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.tinystore.tests.performance.support.GatewayClient;
import com.github.spud.tinystore.tests.performance.support.PostgresClient;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 库存不超卖一致性测试 (200并发 - 不同tradeId，竞争同一SKU库存)
 * 
 * 目的：验证高并发下，库存预占机制能够正确控制库存，不发生超卖
 * 
 * 初始条件（基于V5__e2e_seed_stock.sql）：
 * - SHOP_A/SKU_A: total_quantity=10000, reserved_quantity=0
 * 
 * 并发场景：
 * - 200个不同tradeId，每个请求购买SKU_A数量1
 * - 库存充足（10000 >> 200），理论上全部请求应该成功
 * 
 * 断言（强，基于DB）：
 * - tinystore_inventory.inventory_stock 中 reserved_quantity <= 10000
 * - tinystore_inventory.inventory_reservation 中 status='RESERVED' 的记录数 = 200
 * - COUNT(trade) = 200（成功创建的订单数）
 * - reserved_quantity 增量 = 200（与成功订单数一致）
 * 
 * 断言（弱，基于HTTP）：
 * - 成功响应（200 + code=0）数量 = 200（库存充足，无失败）
 */
class InventoryNoOversellConsistencyIT {

    private static final Logger log = LoggerFactory.getLogger(InventoryNoOversellConsistencyIT.class);

    private String gatewayBaseUrl;
    private int concurrency;
    private String pgUrl;
    private String pgUser;
    private String pgPassword;
    private int timeoutSeconds;

    private GatewayClient gatewayClient;
    private PostgresClient pgClient;

    @BeforeEach
    void setUp() {
        gatewayBaseUrl = System.getProperty("gateway.base.url", "http://localhost:8080");
        concurrency = Integer.parseInt(System.getProperty("perf.concurrency", "200"));
        pgUrl = System.getProperty("pg.url", "jdbc:postgresql://localhost:5433/tinystore");
        pgUser = System.getProperty("pg.user", "postgres");
        pgPassword = System.getProperty("pg.password", "postgres");
        timeoutSeconds = Integer.parseInt(System.getProperty("perf.timeoutSeconds", "120"));

        gatewayClient = new GatewayClient(gatewayBaseUrl);
        pgClient = new PostgresClient(pgUrl, pgUser, pgPassword);
    }

    @AfterEach
    void tearDown() {
        if (pgClient != null) {
            pgClient.close();
        }
    }

    @Test
    void concurrentCreateTrade_competingForSameStock_shouldNotOversell() throws Exception {
        // Given: 初始库存检查
        PostgresClient.InventoryStock initialStock = pgClient.getInventoryStock("SHOP_A", "SKU_A");
        log.info("Initial stock: total={}, reserved={}", initialStock.totalQuantity, initialStock.reservedQuantity);
        
        long maxAllowedReservations = initialStock.totalQuantity - initialStock.reservedQuantity;
        assertThat(maxAllowedReservations).as("Initial available stock should be 10000").isGreaterThanOrEqualTo(10000);

        // Given: 固定的testRun前缀，用于后续DB查询隔离
        String testRunPrefix = "perf-oversell-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        String buyerId = "buyer-perf-" + System.currentTimeMillis();

        // When: 200并发下单（每个不同tradeId + idempotencyKey）
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        List<HttpResponse<String>> responses = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待同时起跑
                    String tradeId = testRunPrefix + index;
                    String idempotencyKey = "idem-" + tradeId;
                    Map<String, Object> requestBody = buildCreateTradeRequest(tradeId, buyerId);
                    
                    HttpResponse<String> response = gatewayClient.createTrade(tradeId, idempotencyKey, requestBody);
                    responses.add(response);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 起跑！
        boolean finished = doneLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All concurrent requests should finish within timeout").isTrue();
        assertThat(errors).as("No exceptions should occur during concurrent requests").isEmpty();

        // Then: HTTP层弱断言
        log.info("Total responses: {}", responses.size());

        long successCount = 0;
        long failureCount = 0;
        for (HttpResponse<String> response : responses) {
            if (response.statusCode() == 200) {
                try {
                    Map<String, Object> body = gatewayClient.parseResponse(response.body());
                    Object code = body.get("code");
                    if (code != null && (Integer) code == 0) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse response body", e);
                    failureCount++;
                }
            } else {
                failureCount++;
            }
        }

        log.info("HTTP success count: {}, failure count: {}", successCount, failureCount);
        assertThat(successCount).as("All requests should succeed (stock is sufficient for 200 concurrent orders)").isEqualTo(concurrency);
        assertThat(successCount).as("Success count should not exceed initial available stock").isLessThanOrEqualTo(maxAllowedReservations);

        // Then: DB层强断言
        PostgresClient.InventoryStock finalStock = pgClient.getInventoryStock("SHOP_A", "SKU_A");
        log.info("Final stock: total={}, reserved={}", finalStock.totalQuantity, finalStock.reservedQuantity);
        assertThat(finalStock.reservedQuantity).as("Reserved quantity must not exceed total quantity").isLessThanOrEqualTo(finalStock.totalQuantity);
        assertThat(finalStock.reservedQuantity).as("Reserved quantity should not exceed initial available + initial reserved").isLessThanOrEqualTo(initialStock.totalQuantity);

        long reservationCount = pgClient.countReservations("SHOP_A", "SKU_A", "RESERVED");
        log.info("DB reservation count (RESERVED) for SHOP_A/SKU_A: {}", reservationCount);
        assertThat(reservationCount).as("Reservation records should not exceed total stock").isLessThanOrEqualTo(finalStock.totalQuantity);

        long tradeCount = pgClient.countSuccessfulTrades(testRunPrefix);
        log.info("DB trade count with prefix {}: {}", testRunPrefix, tradeCount);
        assertThat(tradeCount).as("Trade count should match success count").isEqualTo(successCount);
        assertThat(tradeCount).as("Trade count should not exceed available stock").isLessThanOrEqualTo(maxAllowedReservations);

        // 核心不变式：reserved_quantity增量 = 成功订单数
        long reservedIncrement = finalStock.reservedQuantity - initialStock.reservedQuantity;
        log.info("Reserved increment: {} (should match success count: {})", reservedIncrement, successCount);
        assertThat(reservedIncrement).as("Reserved increment should match trade success count").isEqualTo(successCount);
    }

    private Map<String, Object> buildCreateTradeRequest(String tradeId, String buyerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("tradeId", tradeId);
        request.put("buyerId", buyerId);
        request.put("buyerNick", "perf-buyer");
        request.put("addressId", "addr-perf-001");
        request.put("traceId", "trace-" + tradeId);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        Map<String, Object> line = new HashMap<>();
        line.put("skuId", "SKU_A");
        line.put("productId", "prod-1");
        line.put("productName", "Product A");
        line.put("shopId", "SHOP_A");
        line.put("sellerId", "seller-A");
        line.put("quantity", 1);
        line.put("priceCents", 1000L);
        line.put("weightGrams", 0L);
        orderLines.add(line);

        request.put("orderLines", orderLines);
        return request;
    }
}
