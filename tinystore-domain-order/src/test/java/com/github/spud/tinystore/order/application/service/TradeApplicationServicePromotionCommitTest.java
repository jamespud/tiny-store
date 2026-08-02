package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
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
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateTradeData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TradeApplicationService} — async promotion commit behind feature flag
 * {@code order.promotion.commit-async-enabled}.
 * <p>
 * Strategy: {@code createTrade} is a large method, so the write-outbox step is extracted into
 * the package-visible {@code writePromotionCommitOutbox(...)} and tested directly; flag branch
 * behavior is verified by mocking the full dependency graph (all collaborators are interfaces /
 * POJO builders) and driving {@code createTrade} end-to-end:
 * <ul>
 *   <li>flag=true  → promotionClient.commit never invoked, PROMOTION_COMMIT outbox event written</li>
 *   <li>flag=false → promotionClient.commit invoked (legacy behavior preserved), no PROMOTION_COMMIT event</li>
 * </ul>
 */
@DisplayName("TradeApplicationService — Async Promotion Commit Feature Flag Tests")
class TradeApplicationServicePromotionCommitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ============ test 1: writePromotionCommitOutbox（包级方法直接验证） ============

    @Test
    @DisplayName("writePromotionCommitOutbox_shouldSavePromotionCommitEventWithPayload")
    void writePromotionCommitOutbox_shouldSavePromotionCommitEventWithPayload() throws Exception {
        // Given
        TradeApplicationService service = spyService(false);
        OutboxEventService outboxEventService =
                (OutboxEventService) ReflectionTestUtils.getField(service, "outboxEventService");

        // When
        service.writePromotionCommitOutbox("trade-001", "quote-001", "hash-001", "trace-001");

        // Then
        verify(outboxEventService).saveEvent(argThat(event -> {
            assertThat(event.getEventType()).isEqualTo(OrderEventType.PROMOTION_COMMIT);
            assertThat(event.getAggregateType()).isEqualTo("PROMOTION");
            assertThat(event.getAggregateId()).isEqualTo("quote-001");
            assertThat(event.getTraceId()).isEqualTo("trace-001");
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getPayloadJson()).contains("\"quoteId\":\"quote-001\"");
            assertThat(event.getPayloadJson()).contains("\"tradeId\":\"trade-001\"");
            assertThat(event.getPayloadJson()).contains("\"inputHash\":\"hash-001\"");
            return true;
        }));
    }

    // ============ test 2: flag=true → 跳过同步 commit，写 outbox ============

    @Test
    @DisplayName("asyncEnabled_shouldSkipSyncPromotionCommitAndWriteOutbox")
    void asyncEnabled_shouldSkipSyncPromotionCommitAndWriteOutbox() throws Exception {
        // Given: flag=true
        TradeApplicationService service = spyService(true);
        PromotionClient promotionClient =
                (PromotionClient) ReflectionTestUtils.getField(service, "promotionClient");
        OutboxEventService outboxEventService =
                (OutboxEventService) ReflectionTestUtils.getField(service, "outboxEventService");

        // When
        CreateTradeData result = service.createTrade("idem-001", buildCommand());

        // Then: 下单成功，未调用同步 commit，写了 PROMOTION_COMMIT outbox
        assertThat(result.getTradeId()).isNotBlank();
        verify(promotionClient, never()).commit(any(), any());
        verify(outboxEventService).saveEvent(
                argThat(event -> event.getEventType() == OrderEventType.PROMOTION_COMMIT));
    }

    // ============ test 3: flag=false → 保持现状同步 commit，不写 PROMOTION_COMMIT outbox ============

    @Test
    @DisplayName("asyncDisabled_shouldKeepSyncPromotionCommitAndSkipCommitOutbox")
    void asyncDisabled_shouldKeepSyncPromotionCommitAndSkipCommitOutbox() throws Exception {
        // Given: flag=false（默认值，保持现状）
        TradeApplicationService service = spyService(false);
        PromotionClient promotionClient =
                (PromotionClient) ReflectionTestUtils.getField(service, "promotionClient");
        OutboxEventService outboxEventService =
                (OutboxEventService) ReflectionTestUtils.getField(service, "outboxEventService");

        // When
        CreateTradeData result = service.createTrade("idem-002", buildCommand());

        // Then: 下单成功，同步 commit 被调用，未写 PROMOTION_COMMIT outbox
        assertThat(result.getTradeId()).isNotBlank();
        verify(promotionClient).commit(any(), any());
        verify(outboxEventService, never()).saveEvent(
                argThat(event -> event.getEventType() == OrderEventType.PROMOTION_COMMIT));
    }

    // ============ helpers ============

    private TradeApplicationService spyService(boolean asyncEnabled) {
        TradeApplicationService service = mock(TradeApplicationService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", OBJECT_MAPPER);
        ReflectionTestUtils.setField(service, "promotionCommitAsyncEnabled", asyncEnabled);

        // 幂等性：允许创建，成功路径不缓存已存在响应
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        when(idempotencyService.tryAcquire(any(), any(), any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "idempotencyService", idempotencyService);

        // promotion quote：OK 状态 + 有效 snapshot
        PromotionClient promotionClient = mock(PromotionClient.class);
        when(promotionClient.quote(any(), any())).thenReturn(buildQuoteResponse());
        ReflectionTestUtils.setField(service, "promotionClient", promotionClient);

        // inventory pre-deduct：成功 + 1 个 occupyPair（与单行 order 对齐）
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

        // 仓储与 outbox：成功路径
        TradeRepository tradeRepository = mock(TradeRepository.class);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "tradeRepository", tradeRepository);

        ShopOrderRepository shopOrderRepository = mock(ShopOrderRepository.class);
        when(shopOrderRepository.saveAll(any())).thenReturn(Boolean.TRUE);
        ReflectionTestUtils.setField(service, "shopOrderRepository", shopOrderRepository);

        PaymentIntentJpaRepository paymentIntentJpaRepository =
                mock(PaymentIntentJpaRepository.class);
        when(paymentIntentJpaRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "paymentIntentJpaRepository",
                paymentIntentJpaRepository);

        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        when(outboxEventService.saveEvent(any(OrderDomainEvent.class))).thenReturn(true);
        ReflectionTestUtils.setField(service, "outboxEventService", outboxEventService);

        return service;
    }

    private CreateTradeCommand buildCommand() {
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

    private PromotionQuoteResponse buildQuoteResponse() {
        return PromotionQuoteResponse.builder()
                .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
                .quoteId("quote-001")
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
}
