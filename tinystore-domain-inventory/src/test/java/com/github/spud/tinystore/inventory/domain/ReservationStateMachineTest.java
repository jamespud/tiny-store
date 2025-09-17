package com.github.spud.tinystore.inventory.domain;

import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

public class ReservationStateMachineTest {

    private Reservation newPending() {
        Instant now = Instant.now();
        return new Reservation(UUID.randomUUID().toString(), "s1", "sku1", 5, ReservationState.PENDING, now.plusSeconds(60), null, now, now, 0L);
    }

    @Test
    void confirmThenIdempotent() {
        Reservation r = newPending();
        r.markConfirmed();
        r.markConfirmed(); // 幂等
        Assertions.assertEquals(ReservationState.CONFIRMED, r.getState());
    }

    @Test
    void releaseAfterConfirmFails() {
        Reservation r = newPending();
        r.markConfirmed();
        Assertions.assertThrows(InventoryBusinessException.class, () -> r.markReleased("reason"));
    }

    @Test
    void expireFlow() {
        Reservation r = newPending();
        r.markExpired();
        Assertions.assertEquals(ReservationState.EXPIRED, r.getState());
    }

    @Test
    void illegalTransition() {
        Reservation r = newPending();
        r.markReleased("r1");
        Assertions.assertThrows(InventoryBusinessException.class, () -> r.markConfirmed());
    }
}

