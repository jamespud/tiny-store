package com.github.spud.tinystore.inventory.test.support;

import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;

import java.time.Instant;
import java.util.UUID;

public final class TestDataFactory {
    private TestDataFactory() {}
    public static Reservation newPendingReservation(String shopId, String skuId, long qty, int expireSeconds) {
        Instant now = Instant.now();
        return new Reservation(UUID.randomUUID().toString(), shopId, skuId, qty, ReservationState.PENDING, now.plusSeconds(expireSeconds), null, now, now, 0L);
    }
}

