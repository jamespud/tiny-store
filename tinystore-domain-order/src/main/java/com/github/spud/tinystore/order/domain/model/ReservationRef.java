package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.ReservationStatus;
import com.github.spud.tinystore.order.domain.enums.ReservationType;

/**
 * @author Spud
 * @date 2025/9/6
 */
public record ReservationRef(String reservationId, ReservationType type, String targetLineId,
                             String skuId, Integer quantity, ReservationStatus status) {

}