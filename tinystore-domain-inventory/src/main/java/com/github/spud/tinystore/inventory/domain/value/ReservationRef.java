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
 * <p>
 * {@code status} and {@code quantity} are populated only when the record is fetched
 * with a pessimistic lock (SELECT FOR UPDATE via {@code findByReservationIdForUpdate}).
 * They are null in all other creation contexts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRef {
    private String shopId;
    private String skuId;
    private String reservationId;
    /** Populated only on FOR-UPDATE reads; null otherwise. */
    private String status;
    /** Populated only on FOR-UPDATE reads; null otherwise. */
    private Integer quantity;
}
