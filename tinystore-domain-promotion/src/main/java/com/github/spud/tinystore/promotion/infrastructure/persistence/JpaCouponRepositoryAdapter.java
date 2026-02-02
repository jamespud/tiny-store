package com.github.spud.tinystore.promotion.infrastructure.persistence;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.domain.model.BudgetType;
import com.github.spud.tinystore.promotion.domain.model.Coupon;
import com.github.spud.tinystore.promotion.domain.model.CouponScopeType;
import com.github.spud.tinystore.promotion.domain.model.CouponStatus;
import com.github.spud.tinystore.promotion.domain.model.CouponType;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;

@Repository
public class JpaCouponRepositoryAdapter implements CouponRepository {

	private final JpaCouponRepository jpaRepository;

	public JpaCouponRepositoryAdapter(JpaCouponRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<Coupon> findById(UUID id) {
		return jpaRepository.findById(id).map(this::toDomain);
	}

	@Override
	public Optional<Coupon> findByCouponNo(String couponNo) {
		return jpaRepository.findByCouponNo(couponNo).map(this::toDomain);
	}

	@Override
	public List<Coupon> findAvailableByUser(String userId, OffsetDateTime now) {
		return List.of();
	}

	@Override
	@Transactional
	public void save(Coupon coupon) {
		jpaRepository.save(toEntity(coupon));
	}

	@Override
	@Transactional
	public boolean updateUsage(UUID couponId, long usedIncrement, long totalStock) {
		int updated = jpaRepository.incrementUsedStock(couponId, usedIncrement, totalStock, LocalDateTime.now());
		return updated > 0;
	}

	@Override
	public List<Coupon> findActiveCouponsForListing(String userId, OffsetDateTime now) {
		LocalDateTime t = toLocal(now);
		return jpaRepository.findByStatusAndActiveTime(CouponStatus.ACTIVE.name(), t).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional
	public void updateBudget(UUID couponId, java.math.BigDecimal amountUsed) {
		jpaRepository.incrementBudgetUsed(couponId, amountUsed, LocalDateTime.now());
	}

	@Override
	public List<Coupon> findByStatus(CouponStatus status, OffsetDateTime asOf) {
		LocalDateTime t = toLocal(asOf);
		return jpaRepository.findByStatusAndActiveTime(status.name(), t).stream().map(this::toDomain).toList();
	}

	private Coupon toDomain(CouponEntity e) {
		Coupon c = new Coupon();
		c.setId(e.getId());
		c.setCouponNo(e.getCouponNo());
		c.setCouponType(e.getCouponType() == null ? null : CouponType.valueOf(e.getCouponType()));
		c.setScopeType(e.getScopeType() == null ? null : CouponScopeType.valueOf(e.getScopeType()));
		c.setShopId(e.getShopId());
		c.setThresholdAmount(e.getThresholdAmount());
		c.setDiscountAmount(e.getDiscountAmount());
		c.setDiscountRate(e.getDiscountRate());
		c.setMaxDiscountAmount(e.getMaxDiscountAmount());
		c.setTotalStock(e.getTotalStock());
		c.setUsedStock(e.getUsedStock());
		c.setStartTime(toOffset(e.getStartTime()));
		c.setEndTime(toOffset(e.getEndTime()));
		c.setMutexGroup(e.getMutexGroup());
		c.setPriority(e.getPriority());
		c.setStatus(e.getStatus() == null ? null : CouponStatus.valueOf(e.getStatus()));
		c.setBudgetType(e.getBudgetType() == null ? null : BudgetType.valueOf(e.getBudgetType()));
		c.setBudgetTotal(e.getBudgetTotal());
		c.setBudgetUsed(e.getBudgetUsed());
		return c;
	}

	private CouponEntity toEntity(Coupon c) {
		CouponEntity e = new CouponEntity();
		e.setId(c.getId());
		e.setCouponNo(c.getCouponNo());
		e.setCouponType(c.getCouponType() == null ? null : c.getCouponType().name());
		e.setScopeType(c.getScopeType() == null ? null : c.getScopeType().name());
		e.setShopId(c.getShopId());
		e.setThresholdAmount(c.getThresholdAmount());
		e.setDiscountAmount(c.getDiscountAmount());
		e.setDiscountRate(c.getDiscountRate());
		e.setMaxDiscountAmount(c.getMaxDiscountAmount());
		e.setTotalStock(c.getTotalStock());
		e.setUsedStock(c.getUsedStock());
		e.setStartTime(toLocal(c.getStartTime()));
		e.setEndTime(toLocal(c.getEndTime()));
		e.setMutexGroup(c.getMutexGroup());
		e.setPriority(c.getPriority());
		e.setStatus(c.getStatus() == null ? null : c.getStatus().name());
		e.setBudgetType(c.getBudgetType() == null ? null : c.getBudgetType().name());
		e.setBudgetTotal(c.getBudgetTotal());
		e.setBudgetUsed(c.getBudgetUsed());
		e.setCreatedAt(LocalDateTime.now());
		e.setUpdatedAt(LocalDateTime.now());
		return e;
	}

	private static LocalDateTime toLocal(OffsetDateTime odt) {
		if (odt == null) {
			return null;
		}
		return odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private static OffsetDateTime toOffset(LocalDateTime ldt) {
		if (ldt == null) {
			return null;
		}
		return ldt.atOffset(ZoneOffset.UTC);
	}
}

