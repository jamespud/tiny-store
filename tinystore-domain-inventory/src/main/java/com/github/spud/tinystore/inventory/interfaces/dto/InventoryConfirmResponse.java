package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Response DTO for POST /api/inventory/reservations/confirm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryConfirmResponse {

    private boolean success;
    private String message;

    @Builder.Default
    private List<ReservationRefDto> confirmedRefs = Collections.emptyList();

    @Builder.Default
    private List<String> conflictReservationIds = Collections.emptyList();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationRefDto {
        private String shopId;
        private String skuId;
        private String reservationId;
    }

    public static InventoryConfirmResponse ok(List<ReservationRefDto> confirmedRefs) {
        return InventoryConfirmResponse.builder()
                .success(true)
                .message("ok")
                .confirmedRefs(confirmedRefs != null ? confirmedRefs : Collections.emptyList())
                .build();
    }

    public static InventoryConfirmResponse conflict(List<String> conflictIds, String message) {
        return InventoryConfirmResponse.builder()
                .success(false)
                .message(message)
                .conflictReservationIds(conflictIds != null ? conflictIds : Collections.emptyList())
                .build();
    }
}
