package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryReleaseResponseV2;
import com.github.spud.tinystore.order.infrastructure.acl.dto.PromotionReleaseResponse;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeApplicationService Payment Flow Tests")
class TradeApplicationServicePaymentFlowTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private PaymentIntentJpaRepository paymentIntentJpaRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PromotionClient promotionClient;

    @Mock
    private InventoryClient inventoryClient;

    private TradeApplicationService tradeApplicationService;

    @BeforeEach
    void setUp() {
        tradeApplicationService = new TradeApplicationService();
        ReflectionTestUtils.setField(tradeApplicationService, "tradeRepository", tradeRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "shopOrderRepository", shopOrderRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "paymentIntentJpaRepository", paymentIntentJpaRepository);
        ReflectionTestUtils.setField(tradeApplicationService, "outboxEventService", outboxEventService);
        ReflectionTestUtils.setField(tradeApplicationService, "idempotencyService", idempotencyService);
        ReflectionTestUtils.setField(tradeApplicationService, "promotionClient", promotionClient);
        ReflectionTestUtils.setField(tradeApplicationService, "inventoryClient", inventoryClient);
        ReflectionTestUtils.setField(tradeApplicationService, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("onPaymentSucceeded - confirm success marks paid and emits paid events")
    void onPaymentSucceeded_confirmSuccess_marksPaidAndEmitsEvents() throws Exception {
        PaymentIntentEntity paymentIntent = paymentIntent("pay-002", "trade-002", 2000L, "CREATED");
        Trade trade = trade("trade-002", 2000L);
        ShopOrder shopOrder = version2ShopOrder("order-002", "trade-002", "shop-002", "sku-002", "res-002");

        when(paymentIntentJpaRepository.findByPaymentId("pay-002")).thenReturn(Optional.of(paymentIntent));
        when(tradeRepository.findByTradeId("trade-002")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-002")).thenReturn(List.of(shopOrder));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentIntentJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventService.saveEvent(any())).thenReturn(true);

        PaymentSucceededCommand command = PaymentSucceededCommand.builder()
            .paymentId("pay-002")
            .tradeId("trade-002")
            .paidAmountCents(2000L)
            .traceId("trace-002")
            .build();

        tradeApplicationService.onPaymentSucceeded("idem-pay-002", command);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getPayStatus()).isEqualTo(PayStatus.PAID);

        ArgumentCaptor<ShopOrder> shopOrderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderRepository).save(shopOrderCaptor.capture());
        assertThat(shopOrderCaptor.getValue().getOrderStatus()).isEqualTo(OrderStatus.PENDING_SHIP);
        assertThat(shopOrderCaptor.getValue().getInventoryStatus()).isEqualTo(InventoryStatus.CONFIRMED.getCode());

        ArgumentCaptor<PaymentIntentEntity> paymentIntentCaptor = ArgumentCaptor.forClass(PaymentIntentEntity.class);
        verify(paymentIntentJpaRepository).save(paymentIntentCaptor.capture());
        assertThat(paymentIntentCaptor.getValue().getStatus()).isEqualTo("PAID");
        assertThat(paymentIntentCaptor.getValue().getPaidAt()).isNotNull();

        ArgumentCaptor<OrderDomainEvent> outboxCaptor = ArgumentCaptor.forClass(OrderDomainEvent.class);
        verify(outboxEventService, times(3)).saveEvent(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues())
            .extracting(OrderDomainEvent::getEventType)
            .containsExactlyInAnyOrder(OrderEventType.INVENTORY_CONFIRM, OrderEventType.TRADE_PAID, OrderEventType.ORDER_PAID);

        verify(outboxEventService, never()).saveEventInNewTransaction(any());
    }

    @Test
    @DisplayName("createTrade - promotion commit failure compensates reserved inventory and promotion")
    void createTrade_promotionCommitFailure_shouldCompensateReservedInventoryAndPromotion() throws Exception {
        when(idempotencyService.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
        when(promotionClient.quote(anyString(), any())).thenReturn(okQuoteResponse(1000L));
        when(inventoryClient.preDeductRedisOnly(anyString(), any()))
            .thenReturn(reserveSuccessResponse("shop-009", "sku-009", "res-009"));
        when(inventoryClient.releaseCanonical(anyString(), any())).thenReturn(InventoryReleaseResponseV2.builder()
            .success(true)
            .build());
        when(promotionClient.release(anyString(), any())).thenReturn(PromotionReleaseResponse.builder()
            .success(true)
            .build());
        doThrow(new RuntimeException("commit failed"))
            .when(promotionClient).commit(anyString(), any());

        assertThatThrownBy(() -> tradeApplicationService.createTrade("idem-create-009",
            createTradeCommand("trade-009", "shop-009", "sku-009", 1000L)))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("Promotion commit failed");

        verify(inventoryClient).releaseCanonical(anyString(), any());
        verify(promotionClient).release(anyString(), any());
        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).saveAll(any());
        verify(paymentIntentJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTrade - saveAll false compensates reserved inventory and promotion")
    void createTrade_saveAllFalse_shouldCompensateReservedInventoryAndPromotion() throws Exception {
        when(idempotencyService.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
        when(promotionClient.quote(anyString(), any())).thenReturn(okQuoteResponse(1100L));
        when(promotionClient.commit(anyString(), any())).thenReturn(null);
        when(inventoryClient.preDeductRedisOnly(anyString(), any()))
            .thenReturn(reserveSuccessResponse("shop-010", "sku-010", "res-010"));
        when(inventoryClient.releaseCanonical(anyString(), any())).thenReturn(InventoryReleaseResponseV2.builder()
            .success(true)
            .build());
        when(promotionClient.release(anyString(), any())).thenReturn(PromotionReleaseResponse.builder()
            .success(true)
            .build());
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.saveAll(any())).thenReturn(false);

        assertThatThrownBy(() -> tradeApplicationService.createTrade("idem-create-010",
            createTradeCommand("trade-010", "shop-010", "sku-010", 1100L)))
            .isInstanceOf(DomainConflictException.class)
            .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode()).isEqualTo("ORDER_SAVE_FAILED"));

        verify(inventoryClient).releaseCanonical(anyString(), any());
        verify(promotionClient).release(anyString(), any());
        verify(paymentIntentJpaRepository, never()).save(any());
        // INVENTORY_RESERVE_DB outbox events are written before saveAll fails (same tx, will rollback);
        // but TRADE_CREATED events should NOT be written (they come after saveAll)
        verify(outboxEventService, never()).saveEvent(argThat(
            (OrderDomainEvent e) -> e.getEventType() == OrderEventType.TRADE_CREATED
                || e.getEventType() == OrderEventType.ORDER_CREATED
                || e.getEventType() == OrderEventType.PAYMENT_INTENT_CREATED));
    }

    @Test
    @DisplayName("createTrade - compensation logical release failure surfaces compensation error")
    void createTrade_compensationLogicalReleaseFailure_shouldSurfaceCompensationError() throws Exception {
        when(idempotencyService.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
        when(promotionClient.quote(anyString(), any())).thenReturn(okQuoteResponse(1200L));
        when(inventoryClient.preDeductRedisOnly(anyString(), any()))
            .thenReturn(reserveSuccessResponse("shop-011", "sku-011", "res-011"));
        when(inventoryClient.releaseCanonical(anyString(), any())).thenReturn(InventoryReleaseResponseV2.builder()
            .success(false)
            .message("release rejected")
            .build());
        when(promotionClient.release(anyString(), any())).thenReturn(PromotionReleaseResponse.builder()
            .success(true)
            .build());
        doThrow(new RuntimeException("commit failed"))
            .when(promotionClient).commit(anyString(), any());

        assertThatThrownBy(() -> tradeApplicationService.createTrade("idem-create-011",
            createTradeCommand("trade-011", "shop-011", "sku-011", 1200L)))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("release rejected")
            .satisfies(ex -> assertThat(((DomainConflictException) ex).getErrorCode())
                .isEqualTo("CREATE_TRADE_COMPENSATION_FAILED"));

        verify(inventoryClient).releaseCanonical(anyString(), any());
        verify(promotionClient).release(anyString(), any());
    }

    @Test
    @DisplayName("createTrade - canonical empty refs should throw and stop persistence")
    void createTrade_canonicalEmptyRefs_shouldThrowAndStopPersistence() throws Exception {
        when(idempotencyService.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
        when(promotionClient.quote(anyString(), any())).thenReturn(okQuoteResponse(1300L));
        when(inventoryClient.preDeductRedisOnly(anyString(), any())).thenReturn(InventoryDeductResponse.builder()
            .success(true)
            .occupyPairs(List.of())
            .build());
        when(promotionClient.release(anyString(), any())).thenReturn(PromotionReleaseResponse.builder()
            .success(true)
            .build());

        assertThatThrownBy(() -> tradeApplicationService.createTrade("idem-create-012",
            createTradeCommand("trade-012", "shop-012", "sku-012", 1300L)))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("empty reservation refs");

        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).saveAll(any());
        verify(paymentIntentJpaRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        verify(promotionClient).release(anyString(), any());
        verify(inventoryClient, never()).releaseCanonical(anyString(), any());
    }

    @Test
    @DisplayName("cancelTrade - closing trade projects inventory status to RELEASED")
    void cancelTrade_shouldProjectReleasedInventoryStatus() throws Exception {
        Trade trade = trade("trade-005", 1500L);
        ShopOrder shopOrder = version2ShopOrder("order-005", "trade-005", "shop-005", "sku-005", "res-005");
        List<String> savedInventoryStatuses = new ArrayList<>();

        when(tradeRepository.findByTradeId("trade-005")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-005")).thenReturn(List.of(shopOrder));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shopOrderRepository.save(any())).thenAnswer(invocation -> {
            ShopOrder savedOrder = invocation.getArgument(0);
            savedInventoryStatuses.add(savedOrder.getInventoryStatus());
            return savedOrder;
        });
        when(inventoryClient.releaseCanonical(anyString(), any())).thenReturn(InventoryReleaseResponseV2.builder()
            .success(true)
            .build());
        when(outboxEventService.saveEvent(any())).thenReturn(true);

        CancelTradeCommand command = CancelTradeCommand.builder()
            .tradeId("trade-005")
            .reason("buyer-cancelled")
            .traceId("trace-005")
            .build();

        tradeApplicationService.cancelTrade("idem-cancel-005", command);

        ArgumentCaptor<ShopOrder> shopOrderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderRepository).save(shopOrderCaptor.capture());
        assertThat(shopOrderCaptor.getValue().getOrderStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(shopOrderCaptor.getValue().getInventoryStatus()).isEqualTo(InventoryStatus.RELEASED.getCode());
        assertThat(savedInventoryStatuses).containsExactly(InventoryStatus.RELEASED.getCode());
        verify(inventoryClient).releaseCanonical(anyString(), any());
    }

    @Test
    @DisplayName("cancelTrade - release failure throws and stops cancellation")
    void cancelTrade_releaseFailure_shouldThrowAndStopCancellation() throws Exception {
        Trade trade = trade("trade-006", 1500L);
        ShopOrder shopOrder = version2ShopOrder("order-006", "trade-006", "shop-006", "sku-006", "res-006");
        List<String> savedInventoryStatuses = new ArrayList<>();

        when(tradeRepository.findByTradeId("trade-006")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-006")).thenReturn(List.of(shopOrder));
        doThrow(new RuntimeException("release failed"))
            .when(inventoryClient).releaseCanonical(anyString(), any());

        CancelTradeCommand command = CancelTradeCommand.builder()
            .tradeId("trade-006")
            .reason("buyer-cancelled")
            .traceId("trace-006")
            .build();

        assertThatThrownBy(() -> tradeApplicationService.cancelTrade("idem-cancel-006", command))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("Inventory release conflict during cancel");

        verify(shopOrderRepository, never()).save(any());
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        assertThat(savedInventoryStatuses).isEmpty();
        verify(inventoryClient).releaseCanonical(anyString(), any());
    }

    @Test
    @DisplayName("cancelTrade - canonical logical release failure throws and stops cancellation")
    void cancelTrade_canonicalLogicalReleaseFailure_shouldThrowAndStopCancellation() throws Exception {
        Trade trade = trade("trade-006b", 1500L);
        ShopOrder shopOrder = version2ShopOrder("order-006b", "trade-006b", "shop-006b", "sku-006b", "res-006b");

        when(tradeRepository.findByTradeId("trade-006b")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-006b")).thenReturn(List.of(shopOrder));
        when(inventoryClient.releaseCanonical(anyString(), any())).thenReturn(InventoryReleaseResponseV2.builder()
            .success(false)
            .message("release rejected")
            .build());

        CancelTradeCommand command = CancelTradeCommand.builder()
            .tradeId("trade-006b")
            .reason("buyer-cancelled")
            .traceId("trace-006b")
            .build();

        assertThatThrownBy(() -> tradeApplicationService.cancelTrade("idem-cancel-006b", command))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("Inventory release conflict during cancel")
            .hasMessageContaining("release rejected");

        verify(shopOrderRepository, never()).save(any());
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        verify(inventoryClient).releaseCanonical(anyString(), any());
    }

    @Test
    @DisplayName("cancelTrade - missing occupy info throws and stops cancellation")
    void cancelTrade_missingOccupyInfo_shouldThrowAndStopCancellation() throws Exception {
        Trade trade = trade("trade-007", 1500L);
        ShopOrder shopOrder = ShopOrder.builder()
            .id(7L)
            .orderId("order-007")
            .tradeId("trade-007")
            .shopId("shop-007")
            .sellerId("seller-shop-007")
            .orderStatus(OrderStatus.PENDING_PAY)
            .inventoryStatus(InventoryStatus.PRE_DEDUCTED.getCode())
            .inventoryReservationRefs(List.of())
            .orderLines(List.of())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        List<String> savedInventoryStatuses = new ArrayList<>();

        when(tradeRepository.findByTradeId("trade-007")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-007")).thenReturn(List.of(shopOrder));

        CancelTradeCommand command = CancelTradeCommand.builder()
            .tradeId("trade-007")
            .reason("buyer-cancelled")
            .traceId("trace-007")
            .build();

        assertThatThrownBy(() -> tradeApplicationService.cancelTrade("idem-cancel-007", command))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("Missing inventory reservation refs during cancel");

        verify(shopOrderRepository, never()).save(any());
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        assertThat(savedInventoryStatuses).isEmpty();
        verify(inventoryClient, never()).releaseCanonical(anyString(), any());
    }

    @Test
    @DisplayName("cancelTrade - partial multi shop release failure leaves local state untouched")
    void cancelTrade_partialMultiShopReleaseFailure_shouldNotPersistLocalCloseState() throws Exception {
        Trade trade = trade("trade-008", 2500L);
        ShopOrder firstShopOrder = version2ShopOrder("order-008-1", "trade-008", "shop-008-1", "sku-008-1", "res-008-1");
        ShopOrder secondShopOrder = version2ShopOrder("order-008-2", "trade-008", "shop-008-2", "sku-008-2", "res-008-2");
        List<String> savedInventoryStatuses = new ArrayList<>();

        when(tradeRepository.findByTradeId("trade-008")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-008")).thenReturn(List.of(firstShopOrder, secondShopOrder));
        doAnswer(invocation -> {
            Object request = invocation.getArgument(1);
            String requestText = String.valueOf(request);
            if (requestText.contains("order-008-2")) {
                throw new RuntimeException("release failed second shop");
            }
            return InventoryReleaseResponseV2.builder()
                .success(true)
                .build();
        }).when(inventoryClient).releaseCanonical(anyString(), any());

        CancelTradeCommand command = CancelTradeCommand.builder()
            .tradeId("trade-008")
            .reason("buyer-cancelled")
            .traceId("trace-008")
            .build();

        assertThatThrownBy(() -> tradeApplicationService.cancelTrade("idem-cancel-008", command))
            .isInstanceOf(DomainConflictException.class)
            .hasMessageContaining("Inventory release conflict during cancel");

        verify(inventoryClient, times(2)).releaseCanonical(anyString(), any());
        verify(shopOrderRepository, never()).save(any());
        verify(tradeRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        assertThat(savedInventoryStatuses).isEmpty();
    }

    @Test
    @DisplayName("onPaymentSucceeded - version2 missing refs should throw and skip paid projection")
    void onPaymentSucceeded_version2MissingRefs_shouldThrowAndSkipPaidProjection() throws Exception {
        PaymentIntentEntity paymentIntent = paymentIntent("pay-009", "trade-009", 1900L, "CREATED");
        Trade trade = trade("trade-009", 1900L);
        ShopOrder shopOrder = ShopOrder.builder()
            .id(9L)
            .orderId("order-009")
            .tradeId("trade-009")
            .shopId("shop-009")
            .sellerId("seller-shop-009")
            .orderStatus(OrderStatus.PENDING_PAY)
            .inventoryStatus(InventoryStatus.PRE_DEDUCTED.getCode())
            .inventoryReservationRefs(List.of())
            .orderLines(List.of())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(paymentIntentJpaRepository.findByPaymentId("pay-009")).thenReturn(Optional.of(paymentIntent));
        when(tradeRepository.findByTradeId("trade-009")).thenReturn(Optional.of(trade));
        when(shopOrderRepository.findByTradeId("trade-009")).thenReturn(List.of(shopOrder));

        PaymentSucceededCommand command = PaymentSucceededCommand.builder()
            .paymentId("pay-009")
            .tradeId("trade-009")
            .paidAmountCents(1900L)
            .traceId("trace-009")
            .build();

        assertThatThrownBy(() -> tradeApplicationService.onPaymentSucceeded("idem-pay-009", command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing inventory reservation refs");

        verify(inventoryClient, never()).confirmReservation(anyString(), any());
        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).save(any());
        verify(paymentIntentJpaRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any());
        verify(outboxEventService, never()).saveEventInNewTransaction(any());
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

    private Trade trade(String tradeId, Long payableAmountCents) {
        return Trade.builder()
            .tradeId(tradeId)
            .buyerId("buyer-" + tradeId)
            .buyerNick("buyer")
            .payStatus(PayStatus.UNPAID)
            .totalAmountCents(payableAmountCents)
            .discountAmountCents(0L)
            .payableAmountCents(payableAmountCents)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private PromotionQuoteResponse okQuoteResponse(long payableAmountCents) {
        return PromotionQuoteResponse.builder()
            .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
            .quoteId("quote-001")
            .snapshot(PromotionQuoteResponse.PricingSnapshot.builder()
                .itemsTotalCents(payableAmountCents)
                .promotionDiscountTotalCents(0L)
                .couponDiscountTotalCents(0L)
                .payableCents(payableAmountCents)
                .version(PromotionQuoteResponse.PricingSnapshot.SnapshotVersion.builder()
                    .inputHash("input-hash-001")
                    .build())
                .lines(List.of())
                .appliedBenefits(List.of())
                .build())
            .changeReasons(List.of())
            .build();
    }

    private InventoryDeductResponse reserveSuccessResponse(String shopId, String skuId, String reservationId) {
        return InventoryDeductResponse.builder()
            .success(true)
            .occupyPairs(List.of(InventoryDeductResponse.OccupyPairDto.builder()
                .shopId(shopId)
                .skuId(skuId)
                .occupyId(reservationId)
                .build()))
            .build();
    }

    private CreateTradeCommand createTradeCommand(String tradeId,
                                                  String shopId,
                                                  String skuId,
                                                  long priceCents) {
        return CreateTradeCommand.builder()
            .tradeId(tradeId)
            .buyerId("buyer-" + tradeId)
            .buyerNick("buyer-" + tradeId)
            .addressId("addr-" + tradeId)
            .traceId("trace-" + tradeId)
            .orderLines(List.of(CreateTradeCommand.OrderLineCommand.builder()
                .skuId(skuId)
                .productId("prod-" + skuId)
                .productName("Product " + skuId)
                .shopId(shopId)
                .sellerId("seller-" + shopId)
                .quantity(1)
                .priceCents(priceCents)
                .weightGrams(0L)
                .build()))
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