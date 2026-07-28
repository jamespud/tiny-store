package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReserveCommand;
import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryReservationRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.domain.value.ReservationRef;
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

    @Mock
    private InventoryDeductRecordRepository deductRecordRepository;

    @Mock
    private com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort metricsPort;

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
    @DisplayName("reserve_idempotentHit_shouldReplayReservationRefs")
    void reserve_idempotentHit_shouldReplayReservationRefs() {
        InventoryReserveCommand command = InventoryReserveCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY)
                .orderId(ORDER_ID)
                .tradeId(TRADE_ID)
                .traceId("trace-idem-001")
                .expireAt(OffsetDateTime.now().plusMinutes(30))
                .items(List.of(InventoryReserveCommand.Item.builder()
                        .shopId(SHOP_ID)
                        .skuId(SKU_ID)
                        .quantity(1)
                        .build()))
                .build();

        when(idempotencyRepository.getDeductOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.of(ORDER_ID));
        when(reservationRepository.findByOperationId(IDEMPOTENCY_KEY)).thenReturn(List.of(
                ReservationRef.builder()
                        .shopId(SHOP_ID)
                        .skuId(SKU_ID)
                        .reservationId(RESERVATION_ID)
                        .build()
        ));

        ReservationResult result = domainService.reserve(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationRefs()).hasSize(1);
        assertThat(result.getReservationRefs().get(0).getReservationId()).isEqualTo(RESERVATION_ID);
        verify(reservationRepository).findByOperationId(IDEMPOTENCY_KEY);
        verify(deductGateway, never()).preDeduct(anyString(), anyString(), anyInt(), anyString());
    }

        @Test
        @DisplayName("reserve_idempotentHit_missingRefs_shouldFail")
        void reserve_idempotentHit_missingRefs_shouldFail() {
                InventoryReserveCommand command = InventoryReserveCommand.builder()
                                .idempotencyKey(IDEMPOTENCY_KEY)
                                .orderId(ORDER_ID)
                                .tradeId(TRADE_ID)
                                .traceId("trace-idem-002")
                                .expireAt(OffsetDateTime.now().plusMinutes(30))
                                .items(List.of(InventoryReserveCommand.Item.builder()
                                                .shopId(SHOP_ID)
                                                .skuId(SKU_ID)
                                                .quantity(1)
                                                .build()))
                                .build();

                when(idempotencyRepository.getDeductOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.of(ORDER_ID));
                when(reservationRepository.findByOperationId(IDEMPOTENCY_KEY)).thenReturn(List.of());

                ReservationResult result = domainService.reserve(command);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).isEqualTo("IDEMPOTENT_REPLAY_MISSING_REFS");
                verify(reservationRepository).findByOperationId(IDEMPOTENCY_KEY);
                verify(deductGateway, never()).preDeduct(anyString(), anyString(), anyInt(), anyString());
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
                assertThat(result.getLackSkuIds()).containsExactly("sku-002");

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

        // Pass 1: read-only pre-check (no lock)
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        // Pass 2: SELECT FOR UPDATE — provides authoritative shopId/skuId/status/quantity
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                        .quantity(2)
                        .build()));
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
        verify(stockRepository).deductConfirmed(SHOP_ID, SKU_ID, 2);
        // Quantity and shopId/skuId must NOT come from command payload — verify via the locked ref path
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
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

        // Pass 1: read-only pre-check (no lock)
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        // Pass 2: SELECT FOR UPDATE — provides authoritative shopId/skuId/status
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                        .build()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.RELEASED),
                isNull(), eq("ORDER_CANCELLED")))
                .thenReturn(true);
        when(deductGateway.rollback(SHOP_ID, SKU_ID, RESERVATION_ID)).thenReturn(true);

        // When
        ReservationResult result = domainService.release(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
        // Redis rollback must use authoritative shopId/skuId from locked ref
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
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

    @Test
    @DisplayName("expire_preDeductedReservation_shouldTransitionToExpired")
    void expire_preDeductedReservation_shouldTransitionToExpired() {
        OffsetDateTime now = OffsetDateTime.now();

        when(reservationRepository.findExpiredCandidateIds(now)).thenReturn(List.of(RESERVATION_ID));
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID).build()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.EXPIRED),
                isNull(), eq("EXPIRED_BY_SCHEDULER")))
                .thenReturn(true);
        when(deductGateway.rollback(SHOP_ID, SKU_ID, RESERVATION_ID)).thenReturn(true);

        int expired = domainService.expireExpiredReservations(now);

        assertThat(expired).isEqualTo(1);
        verify(reservationRepository).findExpiredCandidateIds(now);
        verify(reservationRepository).transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.EXPIRED),
                isNull(), eq("EXPIRED_BY_SCHEDULER"));
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
    }

    @Test
    @DisplayName("expire_concurrentConfirmRace_shouldSkipExpiredTransition")
    void expire_concurrentConfirmRace_shouldSkipExpiredTransition() {
        OffsetDateTime now = OffsetDateTime.now();

        when(reservationRepository.findExpiredCandidateIds(now)).thenReturn(List.of(RESERVATION_ID));
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID).build()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.EXPIRED),
                isNull(), eq("EXPIRED_BY_SCHEDULER")))
                .thenReturn(false);

        int expired = domainService.expireExpiredReservations(now);

        assertThat(expired).isEqualTo(0);
        verify(reservationRepository).transitionStatus(
                eq(RESERVATION_ID),
                eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.EXPIRED),
                isNull(), eq("EXPIRED_BY_SCHEDULER"));
    }

    // ==================== new: Fix-1 reserve DB failure ====================

    @Test
    @DisplayName("reserve_dbWriteFailure_shouldThrowIllegalStateException")
    void reserve_dbWriteFailure_shouldThrowIllegalStateException() {
        InventoryReserveCommand command = InventoryReserveCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY)
                .orderId(ORDER_ID)
                .tradeId(TRADE_ID)
                .expireAt(OffsetDateTime.now().plusMinutes(30))
                .items(List.of(InventoryReserveCommand.Item.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).quantity(1).build()))
                .build();

        when(idempotencyRepository.getDeductOrderId(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(deductGateway.preDeduct(SHOP_ID, SKU_ID, 1, ORDER_ID)).thenReturn(Optional.of(RESERVATION_ID));
        doThrow(new RuntimeException("DB error")).when(reservationRepository)
                .saveReservation(any(), any(), any(), anyInt(), any(), any(), any(), any());

        // Must throw, NOT return a fail result — @Transactional must roll back previously saved rows
        assertThatThrownBy(() -> domainService.reserve(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_WRITE_FAILED");
        // Redis admission for the failed item must be rolled back before throwing
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
    }

    // ==================== new: Fix-2 confirm not-found system exception ====================

    @Test
    @DisplayName("confirm_reservationNotFound_shouldThrowIllegalStateException")
    void confirm_reservationNotFound_shouldThrowIllegalStateException() {
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-notfound")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty());

        // Must throw system exception (not return conflict) — per failure-arbitration.md §5
        assertThatThrownBy(() -> domainService.confirm(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVATION_NOT_FOUND");
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
    }

    // ==================== new: Fix-6 confirm idempotent replay ====================

    @Test
    @DisplayName("confirm_idempotentHit_shouldReplayFromDBAuthoritativeData")
    void confirm_idempotentHit_shouldReplayFromDBAuthoritativeData() {
        String dbShopId = "shop-DB-auth"; // intentionally different from command payload to test tamper prevention
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-idem")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId("payload-shop").skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.exists(PAYMENT_ID + ":confirm-idem")).thenReturn(true);
        // Must query DB-authoritative data, not use command payload
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(dbShopId).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.CONFIRMED.getCode())
                        .quantity(2)
                        .build()));

        ReservationResult result = domainService.confirm(command);

        // Must return the DB-authoritative refs (not command payload)
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationRefs()).hasSize(1);
        assertThat(result.getReservationRefs().get(0).getReservationId()).isEqualTo(RESERVATION_ID);
        // Must use DB shopId, not command payload
        assertThat(result.getReservationRefs().get(0).getShopId()).isEqualTo(dbShopId);
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
        verify(reservationRepository, never()).findStatusByReservationId(any());
        verify(stockRepository, never()).deductConfirmed(any(), any(), anyInt());
    }

    @Test
    @DisplayName("confirm_idempotentHit_notFound_shouldThrow")
    void confirm_idempotentHit_notFound_shouldThrow() {
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-idem-notfound")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.exists(PAYMENT_ID + ":confirm-idem-notfound")).thenReturn(true);
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.empty());

        // Must throw system exception (not return conflict)
        assertThatThrownBy(() -> domainService.confirm(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVATION_NOT_FOUND during idempotent replay");
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
    }

    @Test
    @DisplayName("confirm_idempotentHit_notConfirmed_shouldThrow")
    void confirm_idempotentHit_notConfirmed_shouldThrow() {
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-idem-notconf")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.exists(PAYMENT_ID + ":confirm-idem-notconf")).thenReturn(true);
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                        .quantity(2)
                        .build()));

        // Must throw system exception (not return conflict)
        assertThatThrownBy(() -> domainService.confirm(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVATION_NOT_CONFIRMED during idempotent replay");
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
    }

    // ==================== new: Fix-10 release idempotent replay ====================

    @Test
    @DisplayName("release_idempotentHit_shouldReplayFromDBAuthoritativeData")
    void release_idempotentHit_shouldReplayFromDBAuthoritativeData() {
        String dbShopId = "shop-DB-auth"; // intentionally different from command payload to test tamper prevention
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":rel-idem")
                .orderId(ORDER_ID)
                .reason("cancel")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId("payload-shop").skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.isReleased(IDEMPOTENCY_KEY + ":rel-idem")).thenReturn(true);
        // Must query DB-authoritative data, not use command payload
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(dbShopId).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.RELEASED.getCode())
                        .build()));

        ReservationResult result = domainService.release(command);

        // Must return the DB-authoritative refs (not command payload)
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationRefs()).hasSize(1);
        assertThat(result.getReservationRefs().get(0).getReservationId()).isEqualTo(RESERVATION_ID);
        // Must use DB shopId, not command payload
        assertThat(result.getReservationRefs().get(0).getShopId()).isEqualTo(dbShopId);
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
        verify(reservationRepository, never()).findStatusByReservationId(any());
        verify(deductGateway, never()).rollback(any(), any(), any());
    }

    @Test
    @DisplayName("release_idempotentHit_notFound_shouldUseCommandPayloadBestEffort")
    void release_idempotentHit_notFound_shouldUseCommandPayloadBestEffort() {
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":rel-idem-notfound")
                .orderId(ORDER_ID)
                .reason("cancel")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.isReleased(IDEMPOTENCY_KEY + ":rel-idem-notfound")).thenReturn(true);
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.empty());

        ReservationResult result = domainService.release(command);

        // Should return success with command payload (best-effort)
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationRefs()).hasSize(1);
        assertThat(result.getReservationRefs().get(0).getReservationId()).isEqualTo(RESERVATION_ID);
        // Uses command payload as fallback
        assertThat(result.getReservationRefs().get(0).getShopId()).isEqualTo(SHOP_ID);
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
    }

    @Test
    @DisplayName("release_idempotentHit_notReleased_shouldThrow")
    void release_idempotentHit_notReleased_shouldThrow() {
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":rel-idem-notrel")
                .orderId(ORDER_ID)
                .reason("cancel")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(idempotencyRepository.isReleased(IDEMPOTENCY_KEY + ":rel-idem-notrel")).thenReturn(true);
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                        .build()));

        // Must throw system exception (not return conflict)
        assertThatThrownBy(() -> domainService.release(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVATION_NOT_RELEASED during idempotent replay");
        verify(reservationRepository).findByReservationIdForUpdate(RESERVATION_ID);
    }

    // ==================== new: Fix-5 Redis exception isolation ====================

    @Test
    @DisplayName("release_redisRollbackException_shouldNotPropagate")
    void release_redisRollbackException_shouldNotPropagate() {
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":rel-redis-ex")
                .orderId(ORDER_ID)
                .reason("cancel")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                        .build()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID), eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.RELEASED), isNull(), eq("cancel")))
                .thenReturn(true);
        doThrow(new RuntimeException("Redis down")).when(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);

        // DB terminal state RELEASED must be preserved; Redis exception must NOT propagate
        ReservationResult result = domainService.release(command);
        assertThat(result.isSuccess()).isTrue();
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
        // failure-arbitration.md §2 principle 3: execution log must be written on Redis failure
        verify(deductRecordRepository).saveRedisRollbackFailed(
                eq(RESERVATION_ID), eq(SHOP_ID), eq(SKU_ID), anyString());
    }

    @Test
    @DisplayName("expire_redisRollbackException_shouldNotPropagate")
    void expire_redisRollbackException_shouldNotPropagate() {
        OffsetDateTime now = OffsetDateTime.now();

        when(reservationRepository.findExpiredCandidateIds(now)).thenReturn(List.of(RESERVATION_ID));
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID).build()));
        when(reservationRepository.transitionStatus(
                eq(RESERVATION_ID), eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.EXPIRED), isNull(), eq("EXPIRED_BY_SCHEDULER")))
                .thenReturn(true);
        doThrow(new RuntimeException("Redis down")).when(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);

        // DB terminal state EXPIRED must be preserved; Redis exception must NOT propagate
        int expired = domainService.expireExpiredReservations(now);
        assertThat(expired).isEqualTo(1);
        verify(deductGateway).rollback(SHOP_ID, SKU_ID, RESERVATION_ID);
        // failure-arbitration.md §2 principle 3: execution log must be written on Redis failure
        verify(deductRecordRepository).saveRedisRollbackFailed(
                eq(RESERVATION_ID), eq(SHOP_ID), eq(SKU_ID), anyString());
    }

    // ==================== new: race condition fixes ====================

    @Test
    @DisplayName("confirm_releasedRaceInRound2_shouldReturnConflict")
    void confirm_releasedRaceInRound2_shouldReturnConflict() {
        // Scenario: item is PRE_DEDUCTED in pass 1, but races to RELEASED before pass 2 lock is acquired.
        // Expected: return conflict (not throw), no writes performed (status-matrix.md line 20).
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-race-rel")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        // Pass 1: reports PRE_DEDUCTED
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        // Pass 2a (SELECT FOR UPDATE): race — item is now RELEASED
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.RELEASED.getCode())
                        .quantity(2)
                        .build()));

        ReservationResult result = domainService.confirm(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConflictReservationIds()).containsExactly(RESERVATION_ID);
        // No DB transition must have been attempted
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        verify(stockRepository, never()).deductConfirmed(any(), any(), anyInt());
    }

    @Test
    @DisplayName("confirm_expiredRaceInRound2_shouldReturnConflict")
    void confirm_expiredRaceInRound2_shouldReturnConflict() {
        // Scenario: item is PRE_DEDUCTED in pass 1, but expires before pass 2 lock is acquired.
        // Expected: return conflict (not throw) — status-matrix.md line 22.
        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-race-exp")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.EXPIRED.getCode())
                        .quantity(2)
                        .build()));

        ReservationResult result = domainService.confirm(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConflictReservationIds()).containsExactly(RESERVATION_ID);
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        verify(stockRepository, never()).deductConfirmed(any(), any(), anyInt());
    }

    @Test
    @DisplayName("release_confirmedRaceInRound2_shouldReturnConflict")
    void release_confirmedRaceInRound2_shouldReturnConflict() {
        // Scenario: item is PRE_DEDUCTED in pass 1, but races to CONFIRMED before pass 2 lock is acquired.
        // Expected: return conflict (not throw), no writes performed (status-matrix.md line 17).
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .idempotencyKey(IDEMPOTENCY_KEY + ":rel-race")
                .orderId(ORDER_ID)
                .reason("ORDER_CANCELLED")
                .occupyPairs(List.of(OccupyPair.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).occupyId(RESERVATION_ID).build()))
                .build();

        // Pass 1: reports PRE_DEDUCTED (no CONFIRMED detected)
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));
        // Pass 2a (SELECT FOR UPDATE): race — item is now CONFIRMED
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(SHOP_ID).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.CONFIRMED.getCode())
                        .build()));

        ReservationResult result = domainService.release(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConflictReservationIds()).containsExactly(RESERVATION_ID);
        // No DB transition or Redis rollback must have been attempted
        verify(reservationRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        verify(deductGateway, never()).rollback(any(), any(), any());
    }

    @Test
    @DisplayName("confirm_mixedBatch_confirmedAndPreDeducted_shouldNotDuplicateRefs")
    void confirm_mixedBatch_confirmedAndPreDeducted_shouldNotDuplicateRefs() {
        // Scenario: batch of 2 items — first already CONFIRMED (idempotent), second PRE_DEDUCTED.
        // Expected: result has exactly 2 refs; CONFIRMED ref uses DB-authoritative shopId/skuId.
        String resId2 = "res-002";
        String dbShopId = "shop-DB-auth"; // intentionally different from command payload to test tamper prevention

        InventoryConfirmCommand command = InventoryConfirmCommand.builder()
                .idempotencyKey(PAYMENT_ID + ":confirm-mixed")
                .paymentId(PAYMENT_ID)
                .tradeId(TRADE_ID)
                .orderId(ORDER_ID)
                .occupyPairs(List.of(
                        OccupyPair.builder().shopId("payload-shop").skuId(SKU_ID).occupyId(RESERVATION_ID).build(),
                        OccupyPair.builder().shopId("payload-shop").skuId(SKU_ID).occupyId(resId2).build()
                ))
                .build();

        // Pass 1: item 1 = CONFIRMED, item 2 = PRE_DEDUCTED
        when(reservationRepository.findStatusByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(InventoryReservationStatus.CONFIRMED.getCode()));
        when(reservationRepository.findStatusByReservationId(resId2))
                .thenReturn(Optional.of(InventoryReservationStatus.PRE_DEDUCTED.getCode()));

        // Pass 2a locked refs — DB-authoritative shopId
        when(reservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(dbShopId).skuId(SKU_ID).reservationId(RESERVATION_ID)
                        .status(InventoryReservationStatus.CONFIRMED.getCode()).quantity(1).build()));
        when(reservationRepository.findByReservationIdForUpdate(resId2))
                .thenReturn(Optional.of(ReservationRef.builder()
                        .shopId(dbShopId).skuId(SKU_ID).reservationId(resId2)
                        .status(InventoryReservationStatus.PRE_DEDUCTED.getCode()).quantity(3).build()));
        when(reservationRepository.transitionStatus(
                eq(resId2), eq(InventoryReservationStatus.PRE_DEDUCTED),
                eq(InventoryReservationStatus.CONFIRMED), any(), isNull()))
                .thenReturn(true);

        ReservationResult result = domainService.confirm(command);

        assertThat(result.isSuccess()).isTrue();
        // Exactly 2 refs — no duplicates (Medium-1 fix)
        assertThat(result.getReservationRefs()).hasSize(2);
        // All refs must use DB-authoritative shopId, not command payload
        assertThat(result.getReservationRefs())
                .allMatch(ref -> dbShopId.equals(ref.getShopId()));
        // DB transition must only be called for the PRE_DEDUCTED item
        verify(reservationRepository).transitionStatus(
                eq(resId2), any(), any(), any(), any());
        verify(reservationRepository, never()).transitionStatus(
                eq(RESERVATION_ID), any(), any(), any(), any());
    }
}
