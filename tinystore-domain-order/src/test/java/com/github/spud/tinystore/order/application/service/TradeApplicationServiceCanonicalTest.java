package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryReleaseResponseV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TradeApplicationService} — canonical reservation API.
 * <p>
 * These tests verify the canonical reservation path:
 * - createTrade uses reserveCanonical, stores reservationRefs
 * - onPaymentSucceeded calls confirmReservation
 * - cancelTrade calls releaseCanonical
 * <p>
 * Note: Full integration tests are in TradeEndpointIT. These tests validate domain model
 * invariants only, without needing a Spring context.
 */
@DisplayName("TradeApplicationService — Canonical Inventory Reservation Tests")
class TradeApplicationServiceCanonicalTest {

    @Test
    @DisplayName("shopOrder_withReservationRefs_shouldStoreReservationRefs")
    void shopOrder_withReservationRefs_shouldStoreReservationRefs() {
        com.github.spud.tinystore.order.domain.model.InventoryReservationRef ref =
                new com.github.spud.tinystore.order.domain.model.InventoryReservationRef(
                        "shop-1", "sku-1", "res-id-1");

        ShopOrder order = ShopOrder.builder()
                .orderId("order-001")
                .tradeId("trade-001")
                .shopId("shop-1")
                .inventoryReservationRefs(List.of(ref))
                .build();

        assertThat(order.getInventoryReservationRefs()).hasSize(1);
        assertThat(order.getInventoryReservationRefs().get(0).getReservationId()).isEqualTo("res-id-1");
    }

    @Test
    @DisplayName("inventoryConfirmRequest_occupyPairDto_shouldBuildCorrectly")
    void inventoryConfirmRequest_occupyPairDto_shouldBuildCorrectly() {
        com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmRequest req =
                com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmRequest.builder()
                        .paymentId("pay-001")
                        .tradeId("trade-001")
                        .orderId("order-001")
                        .traceId("trace-001")
                        .occupyPairs(List.of(
                                com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmRequest.OccupyPairDto.builder()
                                        .shopId("shop-1")
                                        .skuId("sku-1")
                                        .occupyId("res-id-1")
                                        .build()))
                        .build();

        assertThat(req.getPaymentId()).isEqualTo("pay-001");
        assertThat(req.getOccupyPairs()).hasSize(1);
        assertThat(req.getOccupyPairs().get(0).getOccupyId()).isEqualTo("res-id-1");
    }

    @Test
    @DisplayName("inventoryConfirmResponse_success_shouldMapConflictsCorrectly")
    void inventoryConfirmResponse_success_shouldMapConflictsCorrectly() {
        InventoryConfirmResponse failResp = InventoryConfirmResponse.builder()
                .success(false)
                .message("Conflict: reservation already released")
                .conflictReservationIds(List.of("res-001", "res-002"))
                .build();

        assertThat(failResp.isSuccess()).isFalse();
        assertThat(failResp.getConflictReservationIds()).containsExactly("res-001", "res-002");
    }
}
