package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for POST /api/inventory/reservations/confirm (ACL layer, order domain).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryConfirmResponse {
    private boolean success;
    private String message;
    private List<ConfirmedRef> confirmedRefs;
    private List<String> conflictReservationIds;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfirmedRef {
        private String shopId;
        private String skuId;
        private String reservationId;
    }
}
