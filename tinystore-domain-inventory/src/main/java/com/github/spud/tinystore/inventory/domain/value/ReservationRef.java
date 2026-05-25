package com.github.spud.tinystore.inventory.domain.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Canonical reservation reference value object (shopId + skuId + reservationId).
 * <p>
 * Replaces OccupyPair in the canonical path.
 * Note: reservationId == occupyId from the legacy V2 path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRef {
    private String shopId;
    private String skuId;
    private String reservationId;
}
