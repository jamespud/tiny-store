package com.github.spud.tinystore.promotion.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.spud.tinystore.promotion.domain.model.UserCoupon;
import com.github.spud.tinystore.promotion.domain.model.UserCouponStatus;

public interface UserCouponRepository {

	boolean existsByUserIdAndCouponId(String userId, UUID couponId);

	void save(UserCoupon userCoupon);

	Optional<UserCoupon> findByLockId(String lockId);

	List<UserCoupon> findLockedBefore(OffsetDateTime expiredAt);

	boolean updateStatus(UUID userCouponId, UserCouponStatus from, UserCouponStatus to, OffsetDateTime now);

	List<UserCoupon> findByUserId(String userId);
}
