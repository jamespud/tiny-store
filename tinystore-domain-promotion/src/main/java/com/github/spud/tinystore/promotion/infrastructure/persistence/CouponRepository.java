package com.github.spud.tinystore.promotion.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.spud.tinystore.promotion.domain.model.Coupon;
import com.github.spud.tinystore.promotion.domain.model.CouponStatus;

public interface CouponRepository {

	Optional<Coupon> findById(UUID id);

	Optional<Coupon> findByCouponNo(String couponNo);

	List<Coupon> findAvailableByUser(String userId, OffsetDateTime now);

	void save(Coupon coupon);

	boolean updateUsage(UUID couponId, long usedIncrement, long totalStock);

	List<Coupon> findActiveCouponsForListing(String userId, OffsetDateTime now);

	void updateBudget(UUID couponId, java.math.BigDecimal amountUsed);

	List<Coupon> findByStatus(CouponStatus status, OffsetDateTime asOf);
}
