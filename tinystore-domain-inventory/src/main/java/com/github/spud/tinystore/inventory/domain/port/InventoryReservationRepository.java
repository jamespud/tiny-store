package com.github.spud.tinystore.inventory.domain.port;

import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.value.ReservationRef;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for canonical inventory reservation lifecycle.
 * <p>
 * All read-for-update operations must use pessimistic locking (SELECT FOR UPDATE).
 */
public interface InventoryReservationRepository {

    /**
     * Find a reservation by its ID with a pessimistic write lock.
     */
    Optional<ReservationRef> findByReservationIdForUpdate(String reservationId);

    /**
     * Find the current status of a reservation (read-only, no lock).
     */
    Optional<String> findStatusByReservationId(String reservationId);

    /**
     * Save a new PRE_DEDUCTED reservation.
     */
    void saveReservation(String reservationId, String shopId, String skuId,
                         int quantity, String tradeId, String orderId,
                         String operationId, OffsetDateTime expireAt);

    /**
     * Transition a reservation to a new status.
     * Uses optimistic/pessimistic lock depending on implementation.
     *
     * @param reservationId    target reservation
     * @param expectedStatus   the status the row must currently have
     * @param targetStatus     the status to transition to
     * @param confirmedAt      non-null only when transitioning to CONFIRMED
     * @param releaseReason    non-null only when transitioning to RELEASED or EXPIRED
     * @return true if the row was updated (CAS success), false if the row was in an unexpected state
     */
    boolean transitionStatus(String reservationId,
                             InventoryReservationStatus expectedStatus,
                             InventoryReservationStatus targetStatus,
                             OffsetDateTime confirmedAt,
                             String releaseReason);

    /**
     * Find all PRE_DEDUCTED reservations whose expireAt is before the given time.
     * Used by the expiry scheduler.
     */
    List<String> findExpiredCandidateIds(OffsetDateTime before);
}
