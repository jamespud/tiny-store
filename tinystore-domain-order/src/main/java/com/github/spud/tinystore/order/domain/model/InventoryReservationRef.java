package com.github.spud.tinystore.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Canonical inventory reservation reference value object (shopId + skuId + reservationId).
 * <p>
 * Used in version 2 orders (canonical reservation path).
 * Note: reservationId == occupyId from the V2 deduct path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationRef {
    private String shopId;
    private String skuId;
    private String reservationId;
}
