package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeIdempotencyRecordEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateTradeData;

/**
 * Review P0-2: the success record for create-trade idempotency must be durable, not just a Redis key written
 * in {@code afterCommit()}.
 *
 * <p>Two failure modes are covered here:
 * <ul>
 *   <li>Redis is unavailable after the DB commit -- the trade is committed, so the request must still succeed
 *       (previously the afterCommit exception propagated as an HTTP failure: the ambiguous commit window);</li>
 *   <li>Redis lost the key (restart, flush) -- a retry with the same key and body must replay the original
 *       trade instead of creating a second one.</li>
 * </ul>
 */
@DisplayName("TradeApplicationService — durable idempotency")
class TradeApplicationServiceDurableIdempotencyTest {

    private static final String KEY = "idem-durable-001";
    private static final String FINGERPRINT = TradeRequestFingerprint.of(command());

    private JpaTradeIdempotencyRecordRepository recordRepository;
    private IdempotencyService idempotencyService;
    private PromotionClient promotionClient;
    private InventoryClient inventoryClient;

    @BeforeEach
    void setUp() {
        recordRepository = mock(JpaTradeIdempotencyRecordRepository.class);
        idempotencyService = mock(IdempotencyService.class);
    }

    @Test
    @DisplayName("a Redis failure while caching the response does not fail the committed create")
    void cacheFailureAfterCommitIsNotFatal() throws Exception {
        TradeApplicationService service = service();
        when(idempotencyService.acquire(any(), any(), any()))
            .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
            .when(idempotencyService).markSucceeded(anyString(), anyString(), anyString(), anyString());

        CreateTradeData result = service.createTrade(KEY, command());

        assertThat(result.getTradeId()).isNotBlank();
        // ...and the durable record is what a retry will replay.
        verify(recordRepository).save(any(TradeIdempotencyRecordEntity.class));
    }

    @Test
    @DisplayName("a retry after the Redis key is gone replays the durable record")
    void retryWithoutRedisReplaysDurableRecord() throws Exception {
        String stored = new ObjectMapper().writeValueAsString(CreateTradeData.builder()
            .tradeId("trade-durable-1")
            .payableAmountCents(1000L)
            .paymentIntentId("pay-durable-1")
            .promotionCommitStatus("PENDING")
            .build());
        when(recordRepository.findById(KEY)).thenReturn(Optional.of(TradeIdempotencyRecordEntity.builder()
            .idempotencyKey(KEY)
            .scope("trade:create")
            .fingerprint(FINGERPRINT)
            .state("COMMITTED")
            .tradeId("trade-durable-1")
            .responseJson(stored)
            .createdAt(LocalDateTime.now())
            .build()));

        TradeApplicationService service = service();
        CreateTradeData replayed = service.createTrade(KEY, command());

        assertThat(replayed.getTradeId()).isEqualTo("trade-durable-1");
        assertThat(replayed.getPaymentIntentId()).isEqualTo("pay-durable-1");
        // Nothing was executed again: no new trade, and Redis was never consulted for a new attempt.
        verify(idempotencyService, never()).acquire(any(), any(), any());
    }

    @Test
    @DisplayName("the same key with a different body is a 409 even against the durable record")
    void durableRecordStillBindsTheBody() {
        when(recordRepository.findById(KEY)).thenReturn(Optional.of(TradeIdempotencyRecordEntity.builder()
            .idempotencyKey(KEY)
            .scope("trade:create")
            .fingerprint("some-other-fingerprint")
            .state("COMMITTED")
            .tradeId("trade-durable-2")
            .responseJson("{}")
            .createdAt(LocalDateTime.now())
            .build()));

        TradeApplicationService service = service();

        assertThatThrownBy(() -> service.createTrade(KEY, command()))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("round-3 P0: losing the durable claim replays the winner instead of running the saga")
    void losingTheClaimReplaysTheWinnerAndSkipsInventory() throws Exception {
        // The window this test pins: Redis lost the key, so acquire() says ACQUIRED -- but the durable row
        // was already claimed by the request that got there first. The loser must never reach promotion or
        // inventory. findById answers empty first (the winner's row is not committed yet when the loser
        // starts) and the committed row after the claim conflict.
        String stored = new ObjectMapper().writeValueAsString(CreateTradeData.builder()
            .tradeId("trade-winner")
            .payableAmountCents(1000L)
            .paymentIntentId("pay-winner")
            .promotionCommitStatus("PENDING")
            .build());
        // service() installs the default "we won the claim" stub, so the conflict stub must come after it.
        TradeApplicationService service = service();
        when(idempotencyService.acquire(any(), any(), any()))
            .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
        when(recordRepository.claimProcessing(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
            .thenReturn(0);
        when(recordRepository.findById(KEY)).thenReturn(Optional.empty(),
            Optional.of(TradeIdempotencyRecordEntity.builder()
                .idempotencyKey(KEY)
                .scope("trade:create")
                .fingerprint(FINGERPRINT)
                .state("COMMITTED")
                .tradeId("trade-winner")
                .responseJson(stored)
                .createdAt(LocalDateTime.now())
                .build()));

        CreateTradeData result = service.createTrade(KEY, command());

        assertThat(result.getTradeId()).isEqualTo("trade-winner");
        verify(recordRepository).claimProcessing(anyString(), anyString(), anyString(),
            any(LocalDateTime.class));
        verify(inventoryClient, never()).preDeductRedisOnly(any(), any());
        verify(promotionClient, never()).quote(any(), any());
    }

    @Test
    @DisplayName("round-3 P0: losing the claim to a *different* body is a 409, not a replay")
    void losingTheClaimWithADifferentBodyIsAConflict() {
        TradeApplicationService service = service();
        when(idempotencyService.acquire(any(), any(), any()))
            .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
        when(recordRepository.claimProcessing(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
            .thenReturn(0);
        when(recordRepository.findById(KEY)).thenReturn(Optional.empty(),
            Optional.of(TradeIdempotencyRecordEntity.builder()
                .idempotencyKey(KEY)
                .scope("trade:create")
                .fingerprint("some-other-fingerprint")
                .state("COMMITTED")
                .tradeId("trade-winner")
                .responseJson("{}")
                .createdAt(LocalDateTime.now())
                .build()));

        assertThatThrownBy(() -> service.createTrade(KEY, command()))
            .isInstanceOf(IdempotencyConflictException.class);
        verify(inventoryClient, never()).preDeductRedisOnly(any(), any());
    }

    @Test
    @DisplayName("round-3 P0: the durable claim is taken before any external effect")
    void claimIsTakenBeforePromotionAndInventory() throws Exception {
        when(idempotencyService.acquire(any(), any(), any()))
            .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);

        TradeApplicationService service = service();
        service.createTrade(KEY, command());

        InOrder order = inOrder(recordRepository, promotionClient, inventoryClient);
        order.verify(recordRepository).claimProcessing(anyString(), anyString(), anyString(),
            any(LocalDateTime.class));
        order.verify(promotionClient).quote(any(), any());
        order.verify(inventoryClient).preDeductRedisOnly(any(), any());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private TradeApplicationService service() {
        TradeApplicationService service = mock(TradeApplicationService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "promotionCommitAsyncEnabled", true);
        ReflectionTestUtils.setField(service, "idempotencyService", idempotencyService);
        ReflectionTestUtils.setField(service, "tradeIdempotencyRecordRepository", recordRepository);
        // Round-3 P0: the durable claim is taken before the saga runs; the real repository inserts the row
        // (returns 1). Here the mock plays the "we won the claim" role unless a test says otherwise.
        org.mockito.Mockito.lenient()
            .when(recordRepository.claimProcessing(anyString(), anyString(), anyString(),
                any(LocalDateTime.class)))
            .thenReturn(1);

        promotionClient = mock(PromotionClient.class);
        when(promotionClient.quote(any(), any())).thenReturn(PromotionQuoteResponse.builder()
            .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
            .quoteId("quote-1")
            .snapshot(PromotionQuoteResponse.PricingSnapshot.builder()
                .itemsTotalCents(1000L)
                .promotionDiscountTotalCents(0L)
                .couponDiscountTotalCents(0L)
                .shippingFeeCents(0L)
                .payableCents(1000L)
                .version(PromotionQuoteResponse.PricingSnapshot.SnapshotVersion.builder()
                    .pricingRulesVersion("v1")
                    .shippingRulesVersion("v1")
                    .inputHash("hash-1")
                    .build())
                .build())
            .build());
        ReflectionTestUtils.setField(service, "promotionClient", promotionClient);

        inventoryClient = mock(InventoryClient.class);
        when(inventoryClient.preDeductRedisOnly(any(), any())).thenReturn(InventoryDeductResponse.builder()
            .success(true)
            .message("ok")
            .occupyPairs(List.of(InventoryDeductResponse.OccupyPairDto.builder()
                .shopId("shop-1")
                .skuId("sku-1")
                .occupyId("res-1")
                .build()))
            .build());
        ReflectionTestUtils.setField(service, "inventoryClient", inventoryClient);

        TradeRepository tradeRepository = mock(TradeRepository.class);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "tradeRepository", tradeRepository);

        ShopOrderRepository shopOrderRepository = mock(ShopOrderRepository.class);
        when(shopOrderRepository.saveAll(any())).thenReturn(Boolean.TRUE);
        ReflectionTestUtils.setField(service, "shopOrderRepository", shopOrderRepository);

        PaymentIntentJpaRepository paymentIntentJpaRepository = mock(PaymentIntentJpaRepository.class);
        when(paymentIntentJpaRepository.save(any(PaymentIntentEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "paymentIntentJpaRepository", paymentIntentJpaRepository);

        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        when(outboxEventService.saveEvent(any(OrderDomainEvent.class))).thenReturn(true);
        ReflectionTestUtils.setField(service, "outboxEventService", outboxEventService);

        return service;
    }

    private static CreateTradeCommand command() {
        return CreateTradeCommand.builder()
            .tradeId("trade-durable-1")
            .buyerId("buyer-1")
            .buyerNick("nick")
            .addressId("addr-1")
            .traceId("trace-1")
            .orderLines(List.of(CreateTradeCommand.OrderLineCommand.builder()
                .skuId("sku-1")
                .productId("prod-1")
                .productName("p1")
                .shopId("shop-1")
                .sellerId("seller-1")
                .quantity(1)
                .priceCents(1000L)
                .weightGrams(100L)
                .build()))
            .build();
    }
}
