package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionReleaseResponse;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;

/**
 * C14: "create trade failed" must imply "the promotion quote was released".
 *
 * <p>The compensation flag used to be armed just before the inventory deduct, so failures between the
 * quote and that point (a rejected re-quote, an invalid quote payload) left a {@code QUOTED} row in
 * {@code promotion.checkout_quote} until {@code QuoteExpiryTask}'s PT5M sweep. These tests pin the
 * invariant to the moment the quote exists, including the case where nothing has been reserved yet.
 */
@DisplayName("TradeApplicationService — quote compensation on create-trade failure")
class TradeApplicationServiceQuoteCompensationTest {

    private static final String QUOTE_ID = "quote-001";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("a rejected re-quote releases the quote it just created")
    void requoteRequiredReleasesTheQuote() {
        TradeApplicationService service = serviceWithQuote(quoteResponse(
                PromotionQuoteResponse.CheckoutResultStatus.REQUOTE_REQUIRED));
        PromotionClient promotionClient = promotionClient(service);
        InventoryClient inventoryClient = inventoryClient(service);

        assertThatThrownBy(() -> service.createTrade("idem-001", command()))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("requires re-quote");   // the original failure still surfaces

        verify(promotionClient).release(any(), argThat(request -> QUOTE_ID.equals(request.getQuoteId())));
        // Nothing was reserved, so inventory compensation must not pretend to have released anything.
        verifyNoInteractions(inventoryClient);
    }

    @Test
    @DisplayName("an invalid quote payload still releases the quote that exists server-side")
    void invalidQuotePayloadReleasesTheQuote() {
        PromotionQuoteResponse withoutSnapshot = quoteResponse(
                PromotionQuoteResponse.CheckoutResultStatus.OK);
        withoutSnapshot.setSnapshot(null);
        TradeApplicationService service = serviceWithQuote(withoutSnapshot);
        PromotionClient promotionClient = promotionClient(service);

        assertThatThrownBy(() -> service.createTrade("idem-002", command()))
            .hasMessageContaining("missing snapshot");

        verify(promotionClient).release(any(), argThat(request -> QUOTE_ID.equals(request.getQuoteId())));
    }

    @Test
    @DisplayName("a failure after a successful reserve still releases both sides")
    void failureAfterReserveCompensatesBoth() {
        TradeApplicationService service = serviceWithQuote(quoteResponse(
                PromotionQuoteResponse.CheckoutResultStatus.OK));
        PromotionClient promotionClient = promotionClient(service);
        // The trade row cannot be written -> failure after the inventory reservation succeeded.
        TradeRepository tradeRepository = (TradeRepository) ReflectionTestUtils.getField(service,
                "tradeRepository");
        when(tradeRepository.save(any(Trade.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createTrade("idem-003", command()))
            .hasMessageContaining("db down");

        verify(promotionClient).release(any(), argThat(request -> QUOTE_ID.equals(request.getQuoteId())));
    }

    @Test
    @DisplayName("a create trade that succeeds releases nothing")
    void successDoesNotCompensate() throws Exception {
        TradeApplicationService service = serviceWithQuote(quoteResponse(
                PromotionQuoteResponse.CheckoutResultStatus.OK));
        PromotionClient promotionClient = promotionClient(service);

        assertThat(service.createTrade("idem-004", command()).getTradeId()).isNotBlank();

        verify(promotionClient, never()).release(any(), any());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static TradeApplicationService serviceWithQuote(PromotionQuoteResponse quote) {
        TradeApplicationService service = mock(TradeApplicationService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", OBJECT_MAPPER);
        ReflectionTestUtils.setField(service, "promotionCommitAsyncEnabled", true);

        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        when(idempotencyService.acquire(any(), any(), any()))
            .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
        ReflectionTestUtils.setField(service, "idempotencyService", idempotencyService);

        PromotionClient promotionClient = mock(PromotionClient.class);
        when(promotionClient.quote(any(), any())).thenReturn(quote);
        when(promotionClient.release(any(), any()))
            .thenReturn(PromotionReleaseResponse.builder().success(true).message("released").build());
        ReflectionTestUtils.setField(service, "promotionClient", promotionClient);

        InventoryClient inventoryClient = mock(InventoryClient.class);
        when(inventoryClient.preDeductRedisOnly(any(), any())).thenReturn(
                InventoryDeductResponse.builder()
                        .success(true)
                        .message("ok")
                        .occupyPairs(List.of(InventoryDeductResponse.OccupyPairDto.builder()
                                .shopId("shop-1")
                                .skuId("sku-1")
                                .occupyId("res-001")
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

    private static PromotionClient promotionClient(TradeApplicationService service) {
        return (PromotionClient) ReflectionTestUtils.getField(service, "promotionClient");
    }

    private static InventoryClient inventoryClient(TradeApplicationService service) {
        return (InventoryClient) ReflectionTestUtils.getField(service, "inventoryClient");
    }

    private static PromotionQuoteResponse quoteResponse(PromotionQuoteResponse.CheckoutResultStatus status) {
        return PromotionQuoteResponse.builder()
                .status(status)
                .quoteId(QUOTE_ID)
                .changeReasons(List.of(PromotionQuoteResponse.ChangeReason.builder()
                        .reason("price changed")
                        .skuId("sku-1")
                        .build()))
                .snapshot(PromotionQuoteResponse.PricingSnapshot.builder()
                        .itemsTotalCents(1000L)
                        .promotionDiscountTotalCents(0L)
                        .couponDiscountTotalCents(0L)
                        .shippingFeeCents(0L)
                        .payableCents(1000L)
                        .version(PromotionQuoteResponse.PricingSnapshot.SnapshotVersion.builder()
                                .pricingRulesVersion("v1")
                                .shippingRulesVersion("v1")
                                .inputHash("hash-001")
                                .build())
                        .build())
                .build();
    }

    private static CreateTradeCommand command() {
        return CreateTradeCommand.builder()
                .tradeId("trade-001")
                .buyerId("buyer-001")
                .buyerNick("nick")
                .addressId("addr-001")
                .traceId("trace-001")
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
