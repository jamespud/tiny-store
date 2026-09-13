package com.github.spud.tinystore.order.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TradeIdGenerator 单元测试。
 */
@DisplayName("业务 ID 生成器测试")
class TradeIdGeneratorTest {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 50_000;
    private static final int TOTAL = THREADS * PER_THREAD;

    @Test
    @DisplayName("三个 ID 类型在 8 线程并发生成 400,000 个时应无重复")
    void concurrentGeneration_shouldProduceUniqueIdsAcrossAllTypes() throws Exception {
        Set<String> ids = ConcurrentHashMap.newKeySet(TOTAL);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        try {
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < PER_THREAD; i++) {
                        ids.add(i % 3 == 0 ? TradeIdGenerator.generateTradeId()
                            : i % 3 == 1 ? TradeIdGenerator.generateOrderId()
                                : TradeIdGenerator.generatePaymentIntentId());
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            pool.shutdown();
        }

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(ids).hasSize(TOTAL);
    }

    @Test
    @DisplayName("生成的 ID 应为 32 位十六进制、无连字符、非空")
    void generatedIds_shouldBe32CharHexWithoutDashes() {
        assertThat(TradeIdGenerator.generateTradeId()).matches("^[0-9a-f]{32}$");
        assertThat(TradeIdGenerator.generateOrderId()).matches("^[0-9a-f]{32}$");
        assertThat(TradeIdGenerator.generatePaymentIntentId()).matches("^[0-9a-f]{32}$");
    }
}
