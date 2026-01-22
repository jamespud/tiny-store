package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;

@Repository
public interface JpaCouponRepository extends JpaRepository<CouponEntity, UUID> {

	Optional<CouponEntity> findByCouponNo(String couponNo);

	@Query("SELECT c FROM CouponEntity c WHERE c.status = :status AND c.startTime <= :now AND c.endTime > :now")
	List<CouponEntity> findByStatusAndActiveTime(@Param("status") String status, @Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE CouponEntity c SET c.usedStock = c.usedStock + :usedIncrement, c.updatedAt = :now " +
		"WHERE c.id = :couponId AND c.usedStock + :usedIncrement <= :totalStock")
	int incrementUsedStock(@Param("couponId") UUID couponId, @Param("usedIncrement") long usedIncrement,
		@Param("totalStock") long totalStock, @Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE CouponEntity c SET c.budgetUsed = COALESCE(c.budgetUsed, 0) + :amountUsed, c.updatedAt = :now WHERE c.id = :couponId")
	int incrementBudgetUsed(@Param("couponId") UUID couponId, @Param("amountUsed") java.math.BigDecimal amountUsed,
		@Param("now") LocalDateTime now);
}

