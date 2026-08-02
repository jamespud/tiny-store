package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the payment gate {@code ensureTradePayable} — only trades with
 * {@code promotionCommitStatus = COMMITTED} may be paid.
 * <ul>
 *   <li>gate directly: PENDING / FAILED rejected with TRADE_NOT_READY_FOR_PAYMENT</li>
 *   <li>gate directly: COMMITTED allowed, missing trade → TRADE_NOT_FOUND</li>
 *   <li>{@code onPaymentSucceeded} enforces the gate only when the async promotion
 *       commit flag ({@code order.promotion.commit-async-enabled}) is enabled;
 *       flag off keeps legacy payment behavior (no gate)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeApplicationService — Payment Gate Tests")
class TradeApplicationServicePaymentGateTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private PaymentIntentJpaRepository paymentIntentJpaRepository;

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
        ReflectionTestUtils.setField(tradeApplicationService, "paymentIntentJpaRepository", paymentIntentJpaRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "outboxEventService", outboxEventService);
        ReflectionTestUtils.setField(tradeApplicationService, "promotionClient", promotionClient);
        ReflectionTestUtils.setField(tradeApplicationService, "objectMapper", new ObjectMapper());
        // 门控仅在异步 promotion commit 启用时生效
        ReflectionTestUtils.setField(tradeApplicationService, "promotionCommitAsyncEnabled", true);
    }

    @Test
    @DisplayName("ensureTradePayable rejects a PENDING trade with TRADE_NOT_READY_FOR_PAYMENT")
    void paymentGate_shouldRejectPendingTrade() {
        // Given
        Trade trade = trade("trade-pending", "PENDING");
        when(tradeRepository.findByTradeId("trade-pending")).thenReturn(Optional.of(trade));

        // When/Then
        assertThatThrownBy(() -> tradeApplicationService.ensureTradePayable("trade-pending"))
                .isInstanceOf(DomainConflictException.class)
                .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode())
                        .isEqualTo("TRADE_NOT_READY_FOR_PAYMENT"));
    }

    @Test
    @DisplayName("ensureTradePayable rejects a FAILED trade with TRADE_NOT_READY_FOR_PAYMENT")
    void paymentGate_shouldRejectFailedTrade() {
        // Given
        Trade trade = trade("trade-failed", "FAILED");
        when(tradeRepository.findByTradeId("trade-failed")).thenReturn(Optional.of(trade));

        // When/Then
        assertThatThrownBy(() -> tradeApplicationService.ensureTradePayable("trade-failed"))
                .isInstanceOf(DomainConflictException.class)
                .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode())
                        .isEqualTo("TRADE_NOT_READY_FOR_PAYMENT"));
    }

    @Test
    @DisplayName("ensureTradePayable allows a COMMITTED trade")
    void paymentGate_shouldAllowCommittedTrade() {
        // Given
        Trade trade = trade("trade-committed", "COMMITTED");
        when(tradeRepository.findByTradeId("trade-committed")).thenReturn(Optional.of(trade));

        // When/Then: 不抛错
        assertThatCode(() -> tradeApplicationService.ensureTradePayable("trade-committed"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ensureTradePayable throws TRADE_NOT_FOUND for a missing trade")
    void paymentGate_shouldThrowTradeNotFound() {
        // Given
        when(tradeRepository.findByTradeId("trade-missing")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> tradeApplicationService.ensureTradePayable("trade-missing"))
                .isInstanceOf(DomainConflictException.class)
                .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode())
                        .isEqualTo("TRADE_NOT_FOUND"));
    }

    @Test
    @DisplayName("onPaymentSucceeded rejects a PENDING trade when async flag enabled, no payment side effects")
    void onPaymentSucceeded_shouldRejectPendingTradeWhenAsyncEnabled() {
        // Given: trade PENDING（回执未到）+ 支付回调
        PaymentIntentEntity paymentIntent = paymentIntent("pay-gate-1", "trade-gate-1", 2000L, "CREATED");
        Trade trade = trade("trade-gate-1", "PENDING");
        when(paymentIntentJpaRepository.findByPaymentId("pay-gate-1")).thenReturn(Optional.of(paymentIntent));
        when(tradeRepository.findByTradeId("trade-gate-1")).thenReturn(Optional.of(trade));

        PaymentSucceededCommand command = PaymentSucceededCommand.builder()
                .paymentId("pay-gate-1")
                .tradeId("trade-gate-1")
                .paidAmountCents(2000L)
                .traceId("trace-gate-1")
                .build();

        // When/Then: 门控拒绝，支付流程不推进（无 paid 投影、无 outbox 事件）
        assertThatThrownBy(() -> tradeApplicationService.onPaymentSucceeded("idem-gate-1", command))
                .isInstanceOf(DomainConflictException.class)
                .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode())
                        .isEqualTo("TRADE_NOT_READY_FOR_PAYMENT"));
        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).save(any());
        verify(paymentIntentJpaRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
    }

    @Test
    @DisplayName("onPaymentSucceeded keeps legacy behavior when async flag disabled (PENDING trade pays)")
    void onPaymentSucceeded_shouldSkipGateWhenAsyncDisabled() throws Exception {
        // Given: flag=false（默认值）→ 门控不生效，PENDING 状态不阻断支付（保持现状）
        ReflectionTestUtils.setField(tradeApplicationService, "promotionCommitAsyncEnabled", false);

        PaymentIntentEntity paymentIntent = paymentIntent("pay-gate-2", "trade-gate-2", 2000L, "CREATED");
        Trade trade = trade("trade-gate-2", "PENDING");
        ShopOrder shopOrder = version2ShopOrder("order-gate-2", "trade-gate-2", "shop-2", "sku-2", "res-2");
        when(paymentIntentJpaRepository.findByPaymentId("pay-gate-2")).thenReturn(Optional.of(paymentIntent));
        when(tradeRepository.findByTradeId("trade-gate-2")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-gate-2")).thenReturn(List.of(shopOrder));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentIntentJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventService.saveEvent(any())).thenReturn(true);

        PaymentSucceededCommand command = PaymentSucceededCommand.builder()
                .paymentId("pay-gate-2")
                .tradeId("trade-gate-2")
                .paidAmountCents(2000L)
                .traceId("trace-gate-2")
                .build();

        // When
        tradeApplicationService.onPaymentSucceeded("idem-gate-2", command);

        // Then: 支付正常推进（trade PAID + TRADE_PAID outbox）
        assertThat(trade.getPayStatus()).isEqualTo(PayStatus.PAID);
        verify(tradeRepository).save(trade);
        verify(outboxEventService).saveEvent(argThat(
                e -> e.getEventType() == OrderEventType.TRADE_PAID));
    }

    // ============ helpers ============

    private Trade trade(String tradeId, String promotionCommitStatus) {
        return Trade.builder()
                .tradeId(tradeId)
                .buyerId("buyer-" + tradeId)
                .buyerNick("buyer")
                .payStatus(PayStatus.UNPAID)
                .promotionCommitStatus(promotionCommitStatus)
                .totalAmountCents(2000L)
                .discountAmountCents(0L)
                .payableAmountCents(2000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PaymentIntentEntity paymentIntent(String paymentId, String tradeId, Long amountCents, String status) {
        return PaymentIntentEntity.builder()
                .paymentId(paymentId)
                .tradeId(tradeId)
                .amountCents(amountCents)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
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
                .inventoryReservationRefs(List.of(new InventoryReservationRef(shopId, skuId, reservationId)))
                .orderLines(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
