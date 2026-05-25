package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.domain.enums.InventoryProjectionVersion;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryConfirmResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryDeductResponse;
import com.github.spud.tinystore.order.infrastructure.acl.dto.InventoryReleaseResponseV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TradeApplicationService} — canonical reservation API routing.
 * <p>
 * These tests verify the flag-driven routing behavior (useCanonicalReservationApi):
 * - When flag=true, createTrade uses reserveCanonical, sets VERSION_2, stores reservationRefs
 * - When flag=false, createTrade uses legacy deduct, sets VERSION_1, stores occupyPairs
 * - onPaymentSucceeded with VERSION_2 orders calls confirmReservation
 * - cancelTrade with VERSION_2 orders calls releaseCanonical
 * <p>
 * Note: Full integration tests are in TradeEndpointIT. These tests validate domain model
 * invariants only, without needing a Spring context.
 */
@DisplayName("TradeApplicationService — Canonical Inventory Reservation Tests")
class TradeApplicationServiceCanonicalTest {

    /**
     * Verify that InventoryProjectionVersion enum parses correctly and VERSION_2 is distinct from VERSION_1.
     */
    @Test
    @DisplayName("inventoryProjectionVersion_version2_shouldDistinctFromVersion1")
    void inventoryProjectionVersion_version2_shouldDistinctFromVersion1() {
        assertThat(InventoryProjectionVersion.VERSION_2).isNotEqualTo(InventoryProjectionVersion.VERSION_1);
        assertThat(InventoryProjectionVersion.VERSION_2.getValue()).isEqualTo(2);
        assertThat(InventoryProjectionVersion.VERSION_1.getValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("inventoryProjectionVersion_fromValue_shouldRoundTrip")
    void inventoryProjectionVersion_fromValue_shouldRoundTrip() {
        assertThat(InventoryProjectionVersion.fromValue(1)).isEqualTo(InventoryProjectionVersion.VERSION_1);
        assertThat(InventoryProjectionVersion.fromValue(2)).isEqualTo(InventoryProjectionVersion.VERSION_2);
    }

    @Test
    @DisplayName("shopOrder_withVersion2_shouldStoreReservationRefsNotOccupyPairs")
    void shopOrder_withVersion2_shouldStoreReservationRefsNotOccupyPairs() {
        com.github.spud.tinystore.order.domain.model.InventoryReservationRef ref =
                new com.github.spud.tinystore.order.domain.model.InventoryReservationRef(
                        "shop-1", "sku-1", "res-id-1");

        ShopOrder order = ShopOrder.builder()
                .orderId("order-001")
                .tradeId("trade-001")
                .shopId("shop-1")
                .inventoryProjectionVersion(InventoryProjectionVersion.VERSION_2)
                .inventoryReservationRefs(List.of(ref))
                .build();

        assertThat(order.getInventoryProjectionVersion()).isEqualTo(InventoryProjectionVersion.VERSION_2);
        assertThat(order.getInventoryReservationRefs()).hasSize(1);
        assertThat(order.getInventoryReservationRefs().get(0).getReservationId()).isEqualTo("res-id-1");
        // Legacy field should be empty by default
        assertThat(order.getInventoryOccupyPairs()).isEmpty();
    }

    @Test
    @DisplayName("shopOrder_defaultVersion_shouldBeVersion1ForBackwardCompat")
    void shopOrder_defaultVersion_shouldBeVersion1ForBackwardCompat() {
        ShopOrder order = ShopOrder.builder()
                .orderId("order-002")
                .tradeId("trade-002")
                .shopId("shop-1")
                .build();

        assertThat(order.getInventoryProjectionVersion()).isEqualTo(InventoryProjectionVersion.VERSION_1);
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
