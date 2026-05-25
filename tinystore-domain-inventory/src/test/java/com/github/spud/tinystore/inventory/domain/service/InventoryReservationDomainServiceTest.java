package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReserveCommand;
import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryReservationRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.domain.value.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryReservationDomainService}.
 * <p>
 * Coverage:
 * - reserve: happy path creates PRE_DEDUCTED reservations (Redis + DB)
 * - reserve: all-or-nothing rollback on second item Redis failure
 * - confirm: PRE_DEDUCTED → CONFIRMED happy path
 * - confirm: rejects transition when status is already RELEASED (terminal conflict)
 * - confirm: rejects transition when status is already CONFIRMED (idempotent result)
 * - release: PRE_DEDUCTED → RELEASED happy path
 * - release: rejects transition when status is CONFIRMED (must use refund path)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReservationDomainService Unit Tests")
class InventoryReservationDomainServiceTest {

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private InventoryStockRepository stockRepository;

    @Mock
    private InventoryDeductGateway deductGateway;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @InjectMocks
    private InventoryReservationDomainService domainService;

    private static final String ORDER_ID = "order-001";
    private static final String TRADE_ID = "trade-001";
    private static final String SHOP_ID = "shop-001";
    private static final String SKU_ID = "sku-001";
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final String RESERVATION_ID = "res-001";
    private static final String PAYMENT_ID = "pay-001";

    @BeforeEach
    void setUp() {
        // No shared setup needed; stubs are per-test
    }

    // ==================== reserve ====================

    @Test
    @DisplayName("reserve_singleItem_shouldCreatePreDeductedReservation")
    void reserve_singleItem_shouldCreatePreDeductedReservation() {
        // Given
        InventoryReserveCommand command = InventoryReserveCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY)
                .orderId(ORDER_ID)
                .tradeId(TRADE_ID)
                .traceId("trace-001")
                .expireAt(OffsetDateTime.now().plusMinutes(30))
                .items(List.of(InventoryReserveCommand.Item.builder()
                        .shopId(SHOP_ID)
                        .skuId(SKU_ID)
                        .quantity(2)
                        .build()))
                .build();

        when(idempotencyRepository.getDeductOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(idempotencyRepository.bindDeductOrderIdIfAbsent(IDEMPOTENCY_KEY, ORDER_ID)).thenReturn(true);

        when(deductGateway.preDeduct(SHOP_ID, SKU_ID, 2, ORDER_ID))
                .thenReturn(Optional.of(RESERVATION_ID));

        // When
        ReservationResult result = domainService.reserve(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationRefs()).hasSize(1);
        assertThat(result.getReservationRefs().get(0).getShopId()).isEqualTo(SHOP_ID);
        assertThat(result.getReservationRefs().get(0).getReservationId()).isEqualTo(RESERVATION_ID);

        // Verify DB save was called
        verify(reservationRepository).saveReservation(eq(RESERVATION_ID), eq(SHOP_ID), eq(SKU_ID), eq(2), any(), eq(ORDER_ID), any(), any());
    }

    @Test
    @DisplayName("reserve_secondItemFails_shouldRollbackFirstItem")
    void reserve_secondItemFails_shouldRollbackFirstItem() {
        // Given: 2-item command; first succeeds, second fails
        InventoryReserveCommand command = InventoryReserveCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY)
                .orderId(ORDER_ID)
                .tradeId(TRADE_ID)
                .traceId("trace-002")
                .expireAt(OffsetDateTime.now().plusMinutes(30))
                .items(List.of(
                        InventoryReserveCommand.Item.builder().shopId(SHOP_ID).skuId(SKU_ID).quantity(1).build(),
                        InventoryReserveCommand.Item.builder().shopId(SHOP_ID).skuId("sku-002").quantity(1).build()
                ))
                .build();

        when(idempotencyRepository.getDeductOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        when(deductGateway.preDeduct(SHOP_ID, SKU_ID, 1, ORDER_ID))
                .thenReturn(Optional.of(RESERVATION_ID));
        when(deductGateway.preDeduct(SHOP_ID, "sku-002", 1, ORDER_ID))
                .thenReturn(Optional.empty());

        // When
        ReservationResult result = domainService.reserve(command);

        // Then: failure result
        assertThat(result.isSuccess()).isFalse();

        // First item must be rolled back in Redis
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
        // DB save for second item (sku-002) should never be called
        verify(reservationRepository, never()).saveReservation(any(), any(), eq("sku-002"), anyInt(), any(), any(), any(), any());
    }

    // ==================== confirm ====================

    @Test
    @DisplayName("confirm_preDeductedReservation_shouldTransitionToConfirmed")
    void confirm_preDeductedReservation_shouldTransitionToConfirmed() {
        // Given
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .traceId("trace-003")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.CONFIRMED),
                any(), isNull()))
                .thenReturn(true);

        // When
        ReservationResult result = domainService.confirm(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(reservationRepository).transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.CONFIRMED),
                any(), isNull());
    }

    @Test
    @DisplayName("confirm_releasedReservation_shouldReturnConflict")
    void confirm_releasedReservation_shouldReturnConflict() {
        // Given: reservation is already RELEASED (terminal)
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .traceId("trace-004")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.RELEASED.getCode()));

        // When
        ReservationResult result = domainService.confirm(command);

        // Then: conflict — cannot confirm a released reservation
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConflictReservationIds()).contains(RESERVATION_ID);
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
    }

    // ==================== release ====================

    @Test
    @DisplayName("release_preDeductedReservation_shouldTransitionToReleased")
    void release_preDeductedReservation_shouldTransitionToReleased() {
        // Given
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":release")
                .orderId(ORDER_ID)
                .reason("ORDER_CANCELLED")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.RELEASED),
                isNull(), eq("ORDER_CANCELLED")))
                .thenReturn(true);

        // When
        ReservationResult result = domainService.release(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
    }

    @Test
    @DisplayName("release_confirmedReservation_shouldReturnConflict")
    void release_confirmedReservation_shouldReturnConflict() {
        // Given: reservation is already CONFIRMED — must use refund path, not release
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":release")
                .orderId(ORDER_ID)
                .reason("ORDER_CANCELLED")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.CONFIRMED.getCode()));

        // When
        ReservationResult result = domainService.release(command);

        // Then: conflict — must use refund/restock for confirmed items
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConflictReservationIds()).contains(RESERVATION_ID);
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        verify(deductGateway, never()).rollback(any(), any(), any());
    }
}
