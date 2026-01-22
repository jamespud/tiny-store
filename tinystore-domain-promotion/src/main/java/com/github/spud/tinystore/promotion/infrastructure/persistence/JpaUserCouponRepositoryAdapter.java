package com.github.spud.tinystore.promotion.infrastructure.persistence;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.domain.model.UserCoupon;
import com.github.spud.tinystore.promotion.domain.model.UserCouponStatus;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;

@Repository
public class JpaUserCouponRepositoryAdapter implements UserCouponRepository {

	private final JpaUserCouponRepository jpaRepository;

	public JpaUserCouponRepositoryAdapter(JpaUserCouponRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public boolean existsByUserIdAndCouponId(String userId, UUID couponId) {
		return jpaRepository.existsByUserIdAndCouponId(userId, couponId);
	}

	@Override
	@Transactional
	public void save(UserCoupon userCoupon) {
		jpaRepository.save(toEntity(userCoupon));
	}

	@Override
	public Optional<UserCoupon> findByLockId(String lockId) {
		return jpaRepository.findByLockId(lockId).map(this::toDomain);
	}

	@Override
	public List<UserCoupon> findLockedBefore(OffsetDateTime expiredAt) {
		LocalDateTime t = toLocal(expiredAt);
		return jpaRepository.findLockedBefore(t).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional
	public boolean updateStatus(UUID userCouponId, UserCouponStatus from, UserCouponStatus to, OffsetDateTime now) {
		int updated = jpaRepository.updateStatus(userCouponId, from.name(), to.name(), toLocal(now));
		return updated > 0;
	}

	@Override
	public List<UserCoupon> findByUserId(String userId) {
		return jpaRepository.findByUserId(userId).stream().map(this::toDomain).toList();
	}

	private UserCoupon toDomain(UserCouponEntity e) {
		UserCoupon uc = new UserCoupon();
		uc.setId(e.getId());
		uc.setUserId(e.getUserId());
		uc.setCouponId(e.getCouponId());
		uc.setCouponNo(e.getCouponNo());
		uc.setReceiveTime(toOffset(e.getReceiveTime()));
		uc.setUseStatus(e.getUseStatus() == null ? null : UserCouponStatus.valueOf(e.getUseStatus()));
		uc.setLockId(e.getLockId());
		uc.setLockExpireTime(toOffset(e.getLockExpireTime()));
		uc.setUsedOrderNo(e.getUsedOrderNo());
		uc.setUsedTime(toOffset(e.getUsedTime()));
		return uc;
	}

	private UserCouponEntity toEntity(UserCoupon uc) {
		UserCouponEntity e = new UserCouponEntity();
		e.setId(uc.getId());
		e.setUserId(uc.getUserId());
		e.setCouponId(uc.getCouponId());
		e.setCouponNo(uc.getCouponNo());
		e.setReceiveTime(toLocal(uc.getReceiveTime()));
		e.setUseStatus(uc.getUseStatus() == null ? null : uc.getUseStatus().name());
		e.setLockId(uc.getLockId());
		e.setLockExpireTime(toLocal(uc.getLockExpireTime()));
		e.setUsedOrderNo(uc.getUsedOrderNo());
		e.setUsedTime(toLocal(uc.getUsedTime()));
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

