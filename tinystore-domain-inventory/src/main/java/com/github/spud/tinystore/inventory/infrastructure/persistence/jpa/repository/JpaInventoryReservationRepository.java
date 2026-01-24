package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryReservationRepository extends JpaRepository<InventoryReservationEntity, String> {

	List<InventoryReservationEntity> findByOperationId(String operationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from InventoryReservationEntity r where r.reservationId = :reservationId")
	Optional<InventoryReservationEntity> findByReservationIdForUpdate(@Param("reservationId") String reservationId);

	@Query("select r from InventoryReservationEntity r where r.status = :status and r.expireAt < :now")
	List<InventoryReservationEntity> findByStatusAndExpireAtBefore(@Param("status") String status, @Param("now") OffsetDateTime now);
}

