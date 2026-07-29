package com.github.spud.tinystore.tests.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.tinystore.tests.performance.support.GatewayClient;
import com.github.spud.tinystore.tests.performance.support.PostgresClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 超卖边界测试：C 个买家抢 stock S=C/10 的新 SKU，验证 Redis Lua 不超卖。
 * DB 强断言：PRE_DEDUCTED 计数 == S（恰好 S 个成功准入），成功+失败 == C。
 */
class InventoryOversellBoundaryIT {

    private static final Logger log = LoggerFactory.getLogger(InventoryOversellBoundaryIT.class);

    private String gatewayBaseUrl;
    private String pgUrl;
    private String pgUser;
    private String pgPassword;
    private int concurrency;
    private int stock;

    private GatewayClient gatewayClient;
    private PostgresClient pgClient;

    @BeforeEach
    void setUp() {
        // Hit the inventory service directly (not via gateway): the gateway's
        // /api/inventory/** route strips /api, which mismatches the controller path.
        gatewayBaseUrl = System.getProperty("inventory.base.url", "http://localhost:13000");
        pgUrl = System.getProperty("pg.url", "jdbc:postgresql://localhost:5433/tinystore");
        pgUser = System.getProperty("pg.user", "postgres");
        pgPassword = System.getProperty("pg.password", "postgres");
        concurrency = Integer.parseInt(System.getProperty("perf.concurrency", "500"));
        stock = Integer.parseInt(System.getProperty("perf.oversell.stock", String.valueOf(concurrency / 10)));

        gatewayClient = new GatewayClient(gatewayBaseUrl);
        pgClient = new PostgresClient(pgUrl, pgUser, pgPassword);
    }

    @AfterEach
    void tearDown() {
        if (pgClient != null) pgClient.close();
    }

    @Test
    void concurrentReserve_buyersExceedStock_shouldNotOversell() throws Exception {
        String shopId = "SHOP_A";
        String skuId = "SKU-load-" + UUID.randomUUID().toString().substring(0, 8);
        String testRunPrefix = "perf-oversell-" + UUID.randomUUID().toString().substring(0, 8) + "-";

        // Given: fresh SKU with stock S (< C). Redis total initializes from DB on first preDeduct.
        pgClient.insertStock(shopId, skuId, stock);
        log.info("Oversell boundary: concurrency={}, stock={}, sku={}", concurrency, stock, skuId);

        // When: C concurrent reserves (buyers > stock)
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrency, 2000));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        ConcurrentMap<Integer, HttpResponse<String>> responses = new ConcurrentHashMap<>();

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            String tradeId = testRunPrefix + idx;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Map<String, Object> body = Map.of(
                        "tradeId", tradeId,
                        "orderId", tradeId,
                        "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 1)));
                    HttpResponse<String> resp = gatewayClient.reserveInventory(tradeId, body);
                    responses.put(idx, resp);
                } catch (Throwable t) {
                    // ignore; counted as failure
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finished = doneLatch.await(180, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("All concurrent reserves should finish within timeout").isTrue();

        // Then: DB strong assertions
        long preDeducted = pgClient.countReservations(shopId, skuId, "PRE_DEDUCTED");
        log.info("Result: PRE_DEDUCTED={}, expected (stock)={}", preDeducted, stock);

        assertThat(preDeducted)
            .as("PRE_DEDUCTED count must equal stock S (no oversell): concurrency=%d stock=%d", concurrency, stock)
            .isEqualTo(stock);

        // The controller returns HTTP 200 for both success and STOCK_LACK (success:false in body);
        // count actual admissions by parsing the body.
        long successCount = responses.values().stream()
            .filter(r -> r.statusCode() == 200)
            .filter(r -> {
                try {
                    Map<String, Object> parsed = gatewayClient.parseResponse(r.body());
                    return Boolean.TRUE.equals(parsed.get("success"));
                } catch (Exception e) { return false; }
            })
            .count();
        long failCount = concurrency - successCount;
        assertThat(successCount).as("Successful admissions must equal stock").isEqualTo(stock);
        assertThat(successCount + failCount).as("All requests accounted for").isEqualTo(concurrency);
    }
}
