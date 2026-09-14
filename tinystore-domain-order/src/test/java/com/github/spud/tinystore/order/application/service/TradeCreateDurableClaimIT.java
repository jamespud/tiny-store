package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeIdempotencyRecordEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateTradeData;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;

/**
 * Review round 3 (P0): the create-trade idempotency row must be a durable <b>claim</b>, not just a durable
 * success record.
 *
 * <p>This is the gate the reviewer asked for, on real PostgreSQL + Redis: while request A is inside the saga
 * (past promotion, and it is going to pre-deduct inventory) with its transaction still open, and the Redis
 * idempotency key is deleted under it (Redis restart / flush), a concurrent request B with the same
 * Idempotency-Key must not start a second saga. Before the durable claim, B acquired the fresh Redis key,
 * re-ran promotion and pre-deducted inventory again -- the DB primary key only decided which of the two
 * success records survived.
 */
@DisplayName("create-trade durable claim (review round 3 P0)")
class TradeCreateDurableClaimIT extends AbstractSpringBootOrderIT {

    private static final String KEY = "idem-durable-claim-001";

    @Autowired
    private TradeApplicationService tradeApplicationService;

    @Autowired
    private TradeJpaRepository tradeRepository;

    @Autowired
    private JpaTradeIdempotencyRecordRepository recordRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        recordRepository.deleteAll();
        tradeRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("idempotency:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("a second request on the same key (Redis key lost mid-flight) replays instead of re-reserving")
    void redisLossMidFlightDoesNotStartASecondSaga() throws Exception {
        AtomicInteger inventoryAdmissions = new AtomicInteger();
        CountDownLatch insidePromotion = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        when(promotionClient.quote(any(), any())).thenAnswer(invocation -> {
            insidePromotion.countDown();
            // Park the first request inside the saga, with its transaction (and its durable claim) open.
            releaseFirst.await(30, TimeUnit.SECONDS);
            return quoteResponse();
        });
        when(inventoryClient.preDeductRedisOnly(any(), any())).thenAnswer(invocation -> {
            inventoryAdmissions.incrementAndGet();
            return InventoryDeductResponse.builder()
                .success(true)
                .message("ok")
                .occupyPairs(List.of(InventoryDeductResponse.OccupyPairDto.builder()
                    .shopId("shop-1")
                    .skuId("sku-1")
                    .occupyId("res-" + inventoryAdmissions.get())
                    .build()))
                .build();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CreateTradeData> first = pool.submit(() -> tradeApplicationService.createTrade(KEY, command()));
            assertThat(insidePromotion.await(30, TimeUnit.SECONDS))
                .withFailMessage("the first request never entered the saga")
                .isTrue();

            // The first request holds the durable claim but has not committed. Redis "restarts": the
            // idempotency key disappears, so a second request sees an empty fast path.
            Set<String> keys = redisTemplate.keys("idempotency:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            Future<CreateTradeData> second = pool.submit(
                () -> tradeApplicationService.createTrade(KEY, command()));
            // Give the second request time to reach the claim and block on it. It must NOT be done, and it
            // must not have caused any additional inventory admission.
            Thread.sleep(2000);
            assertThat(second.isDone())
                .withFailMessage("the second request finished while the first one still held the claim -- "
                    + "it ran the saga instead of waiting (inventory admissions: %d)",
                    inventoryAdmissions.get())
                .isFalse();
            assertThat(inventoryAdmissions.get()).isZero();

            releaseFirst.countDown();
            CreateTradeData firstResult = first.get(60, TimeUnit.SECONDS);
            CreateTradeData secondResult = second.get(60, TimeUnit.SECONDS);

            assertThat(secondResult.getTradeId())
                .withFailMessage("the losing request must replay the winner's trade")
                .isEqualTo(firstResult.getTradeId());
            assertThat(inventoryAdmissions.get())
                .withFailMessage("inventory must be admitted exactly once for one Idempotency-Key")
                .isEqualTo(1);
            assertThat(tradeRepository.count()).isEqualTo(1L);

            TradeIdempotencyRecordEntity record = recordRepository.findById(KEY).orElseThrow();
            assertThat(record.getState()).isEqualTo("COMMITTED");
            assertThat(record.getTradeId()).isEqualTo(firstResult.getTradeId());
        }
        finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }

    private static PromotionQuoteResponse quoteResponse() {
        return PromotionQuoteResponse.builder()
            .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
            .quoteId("quote-durable-claim")
            .snapshot(PromotionQuoteResponse.PricingSnapshot.builder()
                .itemsTotalCents(1000L)
                .promotionDiscountTotalCents(0L)
                .couponDiscountTotalCents(0L)
                .shippingFeeCents(0L)
                .payableCents(1000L)
                .version(PromotionQuoteResponse.PricingSnapshot.SnapshotVersion.builder()
                    .pricingRulesVersion("v1")
                    .shippingRulesVersion("v1")
                    .inputHash("hash-durable-claim")
                    .build())
                .build())
            .build();
    }

    /** No client-supplied tradeId: each saga would generate its own, so a duplicate saga is visible. */
    private static CreateTradeCommand command() {
        return CreateTradeCommand.builder()
            .buyerId("buyer-claim-1")
            .buyerNick("claim")
            .addressId("addr-claim-1")
            .traceId("trace-claim-1")
            .orderLines(List.of(CreateTradeCommand.OrderLineCommand.builder()
                .skuId("SKU_IT_001")
                .productId("PROD_IT_001")
                .productName("Test Product")
                .shopId("SHOP_IT_001")
                .sellerId("SELLER_IT_001")
                .quantity(1)
                .priceCents(1000L)
                .weightGrams(100L)
                .build()))
            .build();
    }
}
