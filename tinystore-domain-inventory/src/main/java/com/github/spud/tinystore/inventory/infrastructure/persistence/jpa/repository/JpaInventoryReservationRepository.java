package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInventoryReservationRepository extends JpaRepository<InventoryReservationEntity, String> {

	List<InventoryReservationEntity> findByOperationId(String operationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from InventoryReservationEntity r where r.reservationId = :reservationId")
	Optional<InventoryReservationEntity> findByReservationIdForUpdate(@Param("reservationId") String reservationId);

	@Query("select r from InventoryReservationEntity r where r.status = :status and r.expireAt < :now")
	List<InventoryReservationEntity> findByStatusAndExpireAtBefore(@Param("status") String status, @Param("now") OffsetDateTime now);

	@Query("select r.status from InventoryReservationEntity r where r.reservationId = :reservationId")
	Optional<String> findStatusByReservationId(@Param("reservationId") String reservationId);

	@Query("select r.quantity from InventoryReservationEntity r where r.reservationId = :reservationId")
	Optional<Integer> findQuantityByReservationId(@Param("reservationId") String reservationId);

	@Query("select r.expireAt from InventoryReservationEntity r where r.reservationId = :reservationId")
	Optional<java.time.OffsetDateTime> findExpireAtByReservationId(@Param("reservationId") String reservationId);

	@Query("select r.reservationId from InventoryReservationEntity r where r.status = 'PRE_DEDUCTED' and r.expireAt < :now")
	List<String> findExpiredCandidateIds(@Param("now") OffsetDateTime now);

	@Query("select coalesce(sum(r.quantity), 0) from InventoryReservationEntity r " +
	       "where r.shopId = :shopId and r.skuId = :skuId and r.status = :status")
	long sumQuantityByShopSkuStatus(@Param("shopId") String shopId,
	                                @Param("skuId") String skuId,
	                                @Param("status") String status);

	@Modifying
	@Query("update InventoryReservationEntity r set r.status = :targetStatus, r.confirmedAt = :confirmedAt, " +
	       "r.releaseReason = :releaseReason " +
	       "where r.reservationId = :reservationId and r.status = :expectedStatus")
	int transitionStatus(@Param("reservationId") String reservationId,
	                     @Param("expectedStatus") String expectedStatus,
	                     @Param("targetStatus") String targetStatus,
	                     @Param("confirmedAt") OffsetDateTime confirmedAt,
	                     @Param("releaseReason") String releaseReason);
}

