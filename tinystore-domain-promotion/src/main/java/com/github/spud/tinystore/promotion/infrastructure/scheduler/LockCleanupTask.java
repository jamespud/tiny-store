package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;

@Component
public class LockCleanupTask {

	private static final Logger log = LoggerFactory.getLogger(LockCleanupTask.class);
	private final JpaUserCouponRepository userCouponRepository;

	public LockCleanupTask(JpaUserCouponRepository userCouponRepository) {
		this.userCouponRepository = userCouponRepository;
	}

	@Transactional
	@Scheduled(fixedDelayString = "PT10M")
	public void cleanupExpiredLocks() {
		LocalDateTime now = LocalDateTime.now();
		List<UserCouponEntity> expiredLocks = userCouponRepository.findLockedBefore(now);
		for (UserCouponEntity coupon : expiredLocks) {
			if (coupon.getLockId() == null || coupon.getLockId().isBlank()) {
				continue;
			}
			int updated = userCouponRepository.unlockByLockId(coupon.getLockId(), now);
			if (updated > 0) {
				log.info("Expired coupon lock released by cleanup task, lockId={}", coupon.getLockId());
			}
		}
	}
}
