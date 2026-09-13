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

	/**
	 * 孤儿判定（C12 safety net）：在候选预扣 member 中，找出 DB 里已经存在权威预约行的那些。
	 *
	 * <p>预扣的 Redis uncommit member 格式是 {@code <orderId>_<timestamp>_<amount>}，
	 * 它<b>就是</b> reservation_id（{@code preDeduct} 返回的 member = occupyId = reservationId）。
	 * 所以「member 是否已在 DB 建立预约」等价于「reservation_id 是否存在」。
	 * 返回存在的那部分 id 后，调用方把「候选 - 存在」判定为真正的孤儿。
	 */
	@Query("select r.reservationId from InventoryReservationEntity r where r.reservationId in :reservationIds")
	List<String> findExistingReservationIds(@Param("reservationIds") java.util.Collection<String> reservationIds);

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
