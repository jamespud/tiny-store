package com.github.spud.tinystore.tests.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.tinystore.tests.performance.support.GatewayClient;
import com.github.spud.tinystore.tests.performance.support.LockWaitSampler;
import com.github.spud.tinystore.tests.performance.support.PostgresClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
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
 * confirm 锁竞争测试：N 个预留 -> N 并发 confirm 同 SKU，验证 SELECT FOR UPDATE 串行化正确 +
 * 无死锁 + total_quantity 正确扣减。锁等待采样在此测试期间运行。
 */
class InventoryConfirmLockContentionIT {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfirmLockContentionIT.class);

    private String gatewayBaseUrl;
    private String pgUrl;
    private String pgUser;
    private String pgPassword;
    private String prometheusUrl;
    private int n;

    private GatewayClient gatewayClient;
    private PostgresClient pgClient;

    @BeforeEach
    void setUp() {
        // Hit the inventory service directly (not via gateway): see InventoryOversellBoundaryIT.
        gatewayBaseUrl = System.getProperty("inventory.base.url", "http://localhost:13000");
        pgUrl = System.getProperty("pg.url", "jdbc:postgresql://localhost:5433/tinystore");
        pgUser = System.getProperty("pg.user", "postgres");
        pgPassword = System.getProperty("pg.password", "postgres");
        prometheusUrl = System.getProperty("inventory.prometheus.url", "http://localhost:13000/actuator/prometheus");
        n = Integer.parseInt(System.getProperty("perf.confirm.concurrency", "500"));

        gatewayClient = new GatewayClient(gatewayBaseUrl);
        pgClient = new PostgresClient(pgUrl, pgUser, pgPassword);
    }

    @AfterEach
    void tearDown() {
        if (pgClient != null) pgClient.close();
    }

    @Test
    void concurrentConfirm_sameSku_shouldSerializeWithoutDeadlock() throws Exception {
        String shopId = "SHOP_A";
        String skuId = "SKU-confirm-" + UUID.randomUUID().toString().substring(0, 8);
        pgClient.insertStock(shopId, skuId, n);
        log.info("Confirm lock-contention: N={}, sku={}", n, skuId);

        // Setup: N sequential reserves -> collect occupyIds.
        // NOTE: orderId becomes the Redis biz_id -> occupyId/reservation_id ("orderId_ts_amt").
        // reservation_id column is VARCHAR(64), so keep orderId short (< 46 chars).
        List<String> occupyIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String shortId = "cs-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> body = Map.of(
                "tradeId", shortId, "orderId", shortId,
                "items", List.of(Map.of("shopId", shopId, "skuId", skuId, "quantity", 1)));
            HttpResponse<String> resp = gatewayClient.reserveInventory(shortId, body);
            assertThat(resp.statusCode()).as("setup reserve %d should succeed (status), body=%s", i, resp.body()).isEqualTo(200);
            Map<String, Object> parsed = gatewayClient.parseResponse(resp.body());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pairs = (List<Map<String, Object>>) parsed.get("occupyPairs");
            occupyIds.add((String) pairs.get(0).get("occupyId"));
        }
        assertThat(occupyIds).hasSize(n);
        long deadlocksBefore = pgClient.countDeadlocks();

        // Run: N concurrent confirms + lock-wait sampling
        try (LockWaitSampler sampler = new LockWaitSampler(pgClient, prometheusUrl)) {
            sampler.start();
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(n, 2000));
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(n);
            ConcurrentMap<Integer, HttpResponse<String>> responses = new ConcurrentHashMap<>();

            for (int i = 0; i < n; i++) {
                final int idx = i;
                final String occupyId = occupyIds.get(i);
                String paymentId = "pay-confirm-" + idx + "-" + UUID.randomUUID();
                String tradeId = "confirm-run-" + idx;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Map<String, Object> body = Map.of(
                            "paymentId", paymentId, "tradeId", tradeId, "orderId", tradeId,
                            "occupyPairs", List.of(Map.of("shopId", shopId, "skuId", skuId, "occupyId", occupyId)));
                        responses.put(idx, gatewayClient.confirmReservation(paymentId, body));
                    } catch (Throwable t) {
                        // counted as failure
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            boolean finished = doneLatch.await(180, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertThat(finished).as("All confirms should finish within timeout").isTrue();

            log.info("Lock-wait: maxWaiters={}, maxHikariActive={}", sampler.getMaxLockWaiters(), sampler.getMaxHikariActive());

            // Then: all CONFIRMED, total_quantity == 0, no deadlocks
            long confirmed = pgClient.countReservations(shopId, skuId, "CONFIRMED");
            assertThat(confirmed).as("All N reservations should be CONFIRMED").isEqualTo(n);
            assertThat(pgClient.getInventoryStock(shopId, skuId).totalQuantity)
                .as("total_quantity should be N - N = 0").isEqualTo(0L);
            long deadlocksAfter = pgClient.countDeadlocks();
            assertThat(deadlocksAfter).as("No deadlocks should occur").isEqualTo(deadlocksBefore);

            long successCount = responses.values().stream().filter(r -> r.statusCode() == 200).count();
            assertThat(successCount).as("All confirms should succeed").isEqualTo(n);
        }
    }
}
