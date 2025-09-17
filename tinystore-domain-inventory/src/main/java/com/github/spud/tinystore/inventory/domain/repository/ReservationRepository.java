package com.github.spud.tinystore.inventory.domain.repository;

import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.model.ReservationState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reservation 仓储接口
 */
public interface ReservationRepository {

	void insert(Reservation reservation);

	Optional<Reservation> find(String reservationId);

	boolean updateState(String reservationId, ReservationState expect, ReservationState target,
		long version, String releaseReason);

	default boolean updateState(String reservationId, ReservationState expect,
		ReservationState target, long version) {
		return updateState(reservationId, expect, target, version, null);
	}

	List<Reservation> findPendingExpired(Instant cutoff, int limit);
}
