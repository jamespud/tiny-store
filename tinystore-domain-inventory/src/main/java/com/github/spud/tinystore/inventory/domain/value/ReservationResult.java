package com.github.spud.tinystore.inventory.domain.value;

import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Result returned by canonical reservation operations (reserve / confirm / release / expire).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResult {

    private boolean success;
    private String message;
    private InventoryReservationStatus resultingStatus;

    @Builder.Default
    private List<ReservationRef> reservationRefs = Collections.emptyList();

    @Builder.Default
    private List<String> conflictReservationIds = Collections.emptyList();

    @Builder.Default
    private List<String> lackSkuIds = Collections.emptyList();

    public static ReservationResult ok(List<ReservationRef> refs, InventoryReservationStatus status) {
        return ReservationResult.builder()
                .success(true)
                .message("ok")
                .resultingStatus(status)
                .reservationRefs(refs != null ? refs : Collections.emptyList())
                .build();
    }

    public static ReservationResult conflict(List<String> conflictIds, String message) {
        return ReservationResult.builder()
                .success(false)
                .message(message)
                .conflictReservationIds(conflictIds != null ? conflictIds : Collections.emptyList())
                .build();
    }

    public static ReservationResult fail(List<String> lackSkuIds, String message) {
        return ReservationResult.builder()
                .success(false)
                .message(message)
                .lackSkuIds(lackSkuIds != null ? lackSkuIds : Collections.emptyList())
                .build();
    }

    public static ReservationResult fail(String message) {
        return fail(Collections.emptyList(), message);
    }
}
