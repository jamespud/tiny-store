package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;

@Repository
public interface JpaUserCouponRepository extends JpaRepository<UserCouponEntity, UUID> {

	boolean existsByUserIdAndCouponId(String userId, UUID couponId);

	Optional<UserCouponEntity> findByLockId(String lockId);

	@Query("SELECT uc FROM UserCouponEntity uc WHERE uc.useStatus = 'LOCKED' AND uc.lockExpireTime < :expiredAt")
	List<UserCouponEntity> findLockedBefore(@Param("expiredAt") LocalDateTime expiredAt);

	@Modifying
	@Query("UPDATE UserCouponEntity uc SET uc.useStatus = :to, uc.updatedAt = :now WHERE uc.id = :id AND uc.useStatus = :from")
	int updateStatus(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE UserCouponEntity uc SET uc.useStatus = 'LOCKED', uc.lockId = :lockId, uc.lockExpireTime = :expireAt, uc.updatedAt = :now " +
		"WHERE uc.id = :id AND uc.useStatus = 'UNUSED'")
	int lockUnused(@Param("id") UUID id, @Param("lockId") String lockId, @Param("expireAt") LocalDateTime expireAt,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE UserCouponEntity uc SET uc.useStatus = 'UNUSED', uc.lockId = NULL, uc.lockExpireTime = NULL, uc.updatedAt = :now " +
		"WHERE uc.lockId = :lockId AND uc.useStatus = 'LOCKED'")
	int unlockByLockId(@Param("lockId") String lockId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE UserCouponEntity uc SET uc.useStatus = 'USED', uc.usedTradeId = :tradeId, uc.usedTime = :usedTime, uc.updatedAt = :now " +
		"WHERE uc.lockId = :lockId AND uc.useStatus = 'LOCKED'")
	int useByLockId(@Param("lockId") String lockId, @Param("tradeId") String tradeId, @Param("usedTime") LocalDateTime usedTime,
		@Param("now") LocalDateTime now);

	List<UserCouponEntity> findByUserId(String userId);

	Optional<UserCouponEntity> findFirstByUserIdAndCouponIdAndUseStatusOrderByReceiveTimeAsc(String userId, UUID couponId,
		String useStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCouponEntity uc WHERE uc.userId = :userId AND uc.couponId = :couponId AND uc.useStatus = :unused")
    Optional<UserCouponEntity> findFirstByUserIdAndCouponIdAndUseStatusForUpdate(String userId, UUID id, String unused);

    @Modifying
    @Query("UPDATE UserCouponEntity uc SET uc.useStatus = :used, uc.usedTradeId = :tradeId, uc.usedTime = :now1, uc.updatedAt = :now " +
           "WHERE uc.id = :id AND uc.useStatus = :unused")
    int markAsUsed(UUID id, String unused, String used, String tradeId, LocalDateTime now, LocalDateTime now1);
}
