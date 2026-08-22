package com.github.spud.tinystore.order.infrastructure.scheduler;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryConfirmReconcilerTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private TradeApplicationService tradeApplicationService;

    private InventoryConfirmReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new InventoryConfirmReconciler(tradeRepository, shopOrderRepository, tradeApplicationService);
        ReflectionTestUtils.setField(reconciler, "redriveDebounceSeconds", 30L);
    }

    @Test
    void reconcile_pendingShopOrder_redrivesConfirm() {
        when(tradeRepository.findPaidTradeIdsSince(any(LocalDateTime.class))).thenReturn(List.of("trade-1"));
        ShopOrder pending = ShopOrder.builder()
                .orderId("order-1").tradeId("trade-1")
                .inventoryStatus(InventoryStatus.PRE_DEDUCTED.getCode())
                .inventoryReservationRefs(List.of(InventoryReservationRef.builder()
                        .shopId("shop-1").skuId("sku-1").reservationId("res-1").build()))
                .shopId("shop-1").sellerId("seller-1").build();
        when(shopOrderRepository.findByTradeIdAndInventoryStatus(eq("trade-1"),
                eq(InventoryStatus.PRE_DEDUCTED.getCode()))).thenReturn(List.of(pending));

        reconciler.reconcileUnconfirmedOrders();

        verify(tradeApplicationService).redriveInventoryConfirm(pending);
    }

    @Test
    void reconcile_noPending_doesNotRedrive() {
        when(tradeRepository.findPaidTradeIdsSince(any(LocalDateTime.class))).thenReturn(List.of("trade-1"));
        when(shopOrderRepository.findByTradeIdAndInventoryStatus(eq("trade-1"),
                eq(InventoryStatus.PRE_DEDUCTED.getCode()))).thenReturn(List.of());

        reconciler.reconcileUnconfirmedOrders();

        verify(tradeApplicationService, never()).redriveInventoryConfirm(any());
    }
}
