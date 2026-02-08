package com.github.spud.tinystore.promotion.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;
import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponReceiveTaskEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponReceiveTaskRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.redis.StockLuaExecutor;

@Component
public class CouponReceiveTaskWorker {

	private final JpaCouponReceiveTaskRepository taskRepository;
	private final JpaCouponRepository couponRepository;
	private final JpaUserCouponRepository userCouponRepository;
	private final StockLuaExecutor stockLuaExecutor;

	public CouponReceiveTaskWorker(JpaCouponReceiveTaskRepository taskRepository, JpaCouponRepository couponRepository,
		JpaUserCouponRepository userCouponRepository, StockLuaExecutor stockLuaExecutor) {
		this.taskRepository = taskRepository;
		this.couponRepository = couponRepository;
		this.userCouponRepository = userCouponRepository;
		this.stockLuaExecutor = stockLuaExecutor;
	}

	@Scheduled(fixedDelayString = "PT2S")
	@Transactional
	public void process() {
		String traceId = UUID.randomUUID().toString();
		MDC.put("traceId", traceId);
		MDC.put(LogConstant.MDC_LOG_ID, traceId);
		try {
			List<CouponReceiveTaskEntity> tasks = taskRepository.findTop100ByStatusOrderByCreatedAtAsc("NEW");
			LocalDateTime now = LocalDateTime.now();
			for (CouponReceiveTaskEntity task : tasks) {
				int claimed = taskRepository.updateStatus(task.getId(), "NEW", "PROCESSING", now);
				if (claimed <= 0) {
					continue;
				}
				try {
					processOne(task, now);
					taskRepository.updateStatus(task.getId(), "PROCESSING", "DONE", LocalDateTime.now());
				} catch (Exception e) {
					String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
					taskRepository.updateStatusAndError(task.getId(), "FAILED", msg, LocalDateTime.now());
				}
			}
		} finally {
			MDC.clear();
		}
	}

	private void processOne(CouponReceiveTaskEntity task, LocalDateTime now) {
		UUID couponId = task.getCouponId();
		if (userCouponRepository.existsByUserIdAndCouponId(task.getUserId(), couponId)) {
			return;
		}
		Optional<CouponEntity> couponOpt = couponRepository.findById(couponId);
		if (couponOpt.isEmpty()) {
			throw new IllegalStateException("COUPON_NOT_FOUND");
		}
		CouponEntity coupon = couponOpt.get();
		int updated = couponRepository.incrementUsedStock(couponId, 1L, coupon.getTotalStock(), now);
		if (updated <= 0) {
			stockLuaExecutor.incrementStock(couponId.toString(), 1L);
			throw new IllegalStateException("OUT_OF_STOCK");
		}

		UserCouponEntity uc = new UserCouponEntity();
		uc.setId(UUID.randomUUID());
		uc.setUserId(task.getUserId());
		uc.setCouponId(couponId);
		uc.setCouponNo(coupon.getCouponNo());
		uc.setReceiveTime(now);
		uc.setUseStatus("UNUSED");
		uc.setCreatedAt(now);
		uc.setUpdatedAt(now);
		userCouponRepository.save(uc);
	}
}
