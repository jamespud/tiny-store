package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TradeApplicationService} — promotion commit result application
 * ({@code applyPromotionCommitResult}) and internal auto-cancel ({@code autoCancelTrade}).
 * <ul>
 *   <li>COMMITTED ack → promotionCommitStatus=COMMITTED, no side effects</li>
 *   <li>FAILED ack → promotionCommitStatus=FAILED + auto-cancel (reuses cancelTrade core flow)</li>
 *   <li>duplicate FAILED ack on already-FAILED/closed trade → ignored (state-machine idempotency)</li>
 *   <li>COMMITTED ack on already-closed trade → ignored + warn (race: timeout close vs late ack)</li>
 *   <li>autoCancelTrade → same core flow as cancelTrade (INVENTORY_RELEASE + close + closed events)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeApplicationService — Promotion Commit Result & Auto-Cancel Tests")
class TradeApplicationServiceAutoCancelTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private PromotionClient promotionClient;

    private TradeApplicationService tradeApplicationService;

    @BeforeEach
    void setUp() {
        tradeApplicationService = new TradeApplicationService();
        ReflectionTestUtils.setField(tradeApplicationService, "tradeRepository", tradeRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "shopOrderRepository", shopOrderRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "outboxEventService", outboxEventService);
        ReflectionTestUtils.setField(tradeApplicationService, "promotionClient", promotionClient);
        ReflectionTestUtils.setField(tradeApplicationService, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("applyPromotionCommitResult(committed=true) sets COMMITTED, no auto-cancel side effects")
    void applyCommitted_shouldSetStatusCommitted() {
        // Given
        Trade trade = trade("trade-1", PayStatus.UNPAID, "PENDING", false);
        when(tradeRepository.findByTradeId("trade-1")).thenReturn(Optional.of(trade));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        tradeApplicationService.applyPromotionCommitResult("trade-1", true, null);

        // Then
        assertThat(trade.getPromotionCommitStatus()).isEqualTo("COMMITTED");
        assertThat(trade.isClosed()).isFalse();
        verify(tradeRepository).save(trade);
        verify(outboxEventService, never()).saveEvent(any());
        verify(promotionClient, never()).release(anyString(), any());
    }

    @Test
    @DisplayName("applyPromotionCommitResult(committed=false) sets FAILED and auto-cancels the trade")
    void applyFailed_shouldAutoCancelTrade() {
        // Given
        Trade trade = trade("trade-1", PayStatus.UNPAID, "PENDING", false);
        ShopOrder shopOrder = version2ShopOrder("order-1", "trade-1", "shop-1", "sku-1", "res-1");
        when(tradeRepository.findByTradeId("trade-1")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-1")).thenReturn(List.of(shopOrder));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventService.saveEvent(any())).thenReturn(true);

        // When
        tradeApplicationService.applyPromotionCommitResult("trade-1", false, "COUPON_UNAVAILABLE");

        // Then: FAILED + auto-cancel 复用核心流程（release 库存 outbox + close + 促销 release）
        assertThat(trade.getPromotionCommitStatus()).isEqualTo("FAILED");
        assertThat(trade.isClosed()).isTrue();
        verify(outboxEventService).saveEvent(
                argThat(e -> e.getEventType() == OrderEventType.INVENTORY_RELEASE));
        verify(outboxEventService).saveEvent(
                argThat(e -> e.getEventType() == OrderEventType.TRADE_CLOSED));
        verify(promotionClient).release(anyString(), any());
    }

    @Test
    @DisplayName("applyPromotionCommitResult(committed=true) on already-closed trade compensates promotion release (late ack race)")
    void applyCommitted_onAlreadyClosedTrade_shouldReleasePromotion() {
        // Given: trade 已关闭（如超时自动取消后回执才到），券已由 promotion 预占
        Trade trade = trade("trade-1", PayStatus.UNPAID, "PENDING", true);
        when(tradeRepository.findByTradeId("trade-1")).thenReturn(Optional.of(trade));

        // When/Then: 状态不变、无本地写操作，但补偿 release 已预占的券
        assertThatCode(() -> tradeApplicationService.applyPromotionCommitResult("trade-1", true, null))
                .doesNotThrowAnyException();
        assertThat(trade.getPromotionCommitStatus()).isEqualTo("PENDING");
        assertThat(trade.isClosed()).isTrue();
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        verify(promotionClient).release(anyString(), argThat(req ->
                "quote-1".equals(req.getQuoteId())
                        && "trade-1".equals(req.getTradeId())
                        && "TRADE_CLOSED_BEFORE_ACK".equals(req.getReason())));
    }


    @Test
    @DisplayName("applyPromotionCommitResult(committed=false) duplicate ack on already-FAILED/closed trade is ignored")
    void applyFailed_onAlreadyFailedTrade_shouldBeIgnored() {
        // Given: 首次 FAILED 已触发自动取消（status=FAILED + closed）
        Trade trade = trade("trade-1", PayStatus.UNPAID, "FAILED", true);
        when(tradeRepository.findByTradeId("trade-1")).thenReturn(Optional.of(trade));

        // When/Then: 重复回执被忽略，不重复触发自动取消
        assertThatCode(() -> tradeApplicationService.applyPromotionCommitResult("trade-1", false, "COUPON_UNAVAILABLE"))
                .doesNotThrowAnyException();
        assertThat(trade.getPromotionCommitStatus()).isEqualTo("FAILED");
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        verify(promotionClient, never()).release(anyString(), any());
    }

    @Test
    @DisplayName("autoCancelTrade reuses cancelTrade core flow (release inventory, close trade, emit closed events)")
    void autoCancelTrade_shouldReuseCancelTradeCoreFlow() {
        // Given
        Trade trade = trade("trade-1", PayStatus.UNPAID, "PENDING", false);
        ShopOrder shopOrder = version2ShopOrder("order-1", "trade-1", "shop-1", "sku-1", "res-1");
        when(tradeRepository.findByTradeId("trade-1")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-1")).thenReturn(List.of(shopOrder));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventService.saveEvent(any())).thenReturn(true);

        // When
        tradeApplicationService.autoCancelTrade("trade-1", "PAYMENT_TIMEOUT", "trace-1");

        // Then: 与 cancelTrade 相同核心流程
        assertThat(trade.isClosed()).isTrue();
        assertThat(shopOrder.getOrderStatus()).isEqualTo(OrderStatus.CLOSED);
        verify(outboxEventService).saveEvent(
                argThat(e -> e.getEventType() == OrderEventType.INVENTORY_RELEASE));
        verify(outboxEventService).saveEvent(
                argThat(e -> e.getEventType() == OrderEventType.TRADE_CLOSED));
        verify(outboxEventService).saveEvent(
                argThat(e -> e.getEventType() == OrderEventType.ORDER_CLOSED));
    }

    // ============ helpers ============

    private Trade trade(String tradeId, PayStatus payStatus, String promotionCommitStatus,
                        boolean closed) {
        Trade.TradeBuilder builder = Trade.builder()
                .tradeId(tradeId)
                .buyerId("buyer-1")
                .buyerNick("buyer")
                .payStatus(payStatus)
                .promotionCommitStatus(promotionCommitStatus)
                .promotionQuoteId("quote-1")
                .totalAmountCents(1500L)
                .discountAmountCents(0L)
                .payableAmountCents(1500L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());
        if (closed) {
            builder.closedAt(LocalDateTime.now());
        }
        return builder.build();
    }

    private ShopOrder version2ShopOrder(String orderId,
                                        String tradeId,
                                        String shopId,
                                        String skuId,
                                        String reservationId) {
        return ShopOrder.builder()
                .id(1L)
                .orderId(orderId)
                .tradeId(tradeId)
                .shopId(shopId)
                .sellerId("seller-" + shopId)
                .orderStatus(OrderStatus.PENDING_PAY)
                .inventoryStatus(InventoryStatus.PRE_DEDUCTED.getCode())
                .inventoryReservationRefs(
                        List.of(new InventoryReservationRef(shopId, skuId, reservationId)))
                .orderLines(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
