package com.github.spud.tinystore.product.domain.repository;

import com.github.spud.tinystore.product.domain.enums.ReservationState;
import com.github.spud.tinystore.product.domain.model.Reservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 预留记录仓储接口（方法签名占位）
 */
public interface ReservationRepository {

	Optional<Reservation> findByReservationId(String reservationId);

	Optional<Reservation> findByOperationId(String operationId);

	boolean insert(Reservation reservation);

	boolean markState(String reservationId, ReservationState from, ReservationState to);

	List<Reservation> findExpiredBatch(int limit, OffsetDateTime now);

	// Noop 实现占位
	class Noop implements ReservationRepository {

		@Override
		public Optional<Reservation> findByReservationId(String reservationId) {
			return Optional.empty();
		}

		@Override
		public Optional<Reservation> findByOperationId(String operationId) {
			return Optional.empty();
		}

		@Override
		public boolean insert(Reservation reservation) {
			return false;
		}

		@Override
		public boolean markState(String reservationId, ReservationState from, ReservationState to) {
			return false;
		}

		@Override
		public List<Reservation> findExpiredBatch(int limit, OffsetDateTime now) {
			return List.of();
		}
	}
}
