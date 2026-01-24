package com.github.spud.tinystore.promotion.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponReceiveTaskEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponReceiveTaskRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.redis.PromotionRedisKeys;
import com.github.spud.tinystore.promotion.infrastructure.redis.StockLuaExecutor;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponReceiveRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponReceiveResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.CouponReceiveStatus;
import com.github.spud.tinystore.promotion.interfaces.dto.UserCouponListResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.UserCouponView;

@Service
public class CouponAssetAppService {

	private static final DateTimeFormatter MINUTE_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

	private final JpaCouponReceiveTaskRepository receiveTaskRepository;
	private final JpaCouponRepository couponRepository;
	private final JpaUserCouponRepository userCouponRepository;
	private final StringRedisTemplate redisTemplate;
	private final StockLuaExecutor stockLuaExecutor;
	private final int userLimitPerMinute;

	public CouponAssetAppService(JpaCouponReceiveTaskRepository receiveTaskRepository,
		JpaCouponRepository couponRepository,
		JpaUserCouponRepository userCouponRepository,
		StringRedisTemplate redisTemplate,
		StockLuaExecutor stockLuaExecutor,
		@Value("${promotion.coupon.receive.user-limit-per-minute:10}") int userLimitPerMinute) {
		this.receiveTaskRepository = receiveTaskRepository;
		this.couponRepository = couponRepository;
		this.userCouponRepository = userCouponRepository;
		this.redisTemplate = redisTemplate;
		this.stockLuaExecutor = stockLuaExecutor;
		this.userLimitPerMinute = userLimitPerMinute;
	}

	@Transactional
	public CouponReceiveResponse receive(String idempotencyKey, CouponReceiveRequest request) {
		String requestHash = sha256(request.getUserId() + "|" + request.getCouponId());
		CouponReceiveTaskEntity existing = receiveTaskRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			if (!requestHash.equals(existing.getRequestHash())) {
				return CouponReceiveResponse.of(CouponReceiveStatus.CONFLICT, existing.getId().toString(), request.getCouponId(),
					"IDEMPOTENCY_CONFLICT");
			}
			CouponReceiveStatus st = "DONE".equals(existing.getStatus()) ? CouponReceiveStatus.RECEIVED : CouponReceiveStatus.PENDING;
			return CouponReceiveResponse.of(st, existing.getId().toString(), request.getCouponId(), existing.getStatus());
		}

		UUID couponId = parseUuid(request.getCouponId());
		if (couponId == null) {
			return CouponReceiveResponse.of(CouponReceiveStatus.NOT_ELIGIBLE, null, request.getCouponId(), "INVALID_COUPON_ID");
		}
		Optional<CouponEntity> couponOpt = couponRepository.findById(couponId);
		if (couponOpt.isEmpty()) {
			return CouponReceiveResponse.of(CouponReceiveStatus.NOT_ELIGIBLE, null, request.getCouponId(), "COUPON_NOT_FOUND");
		}
		CouponEntity coupon = couponOpt.get();
		LocalDateTime now = LocalDateTime.now();
		if (!"ACTIVE".equals(coupon.getStatus()) || coupon.getStartTime().isAfter(now) || !coupon.getEndTime().isAfter(now)) {
			return CouponReceiveResponse.of(CouponReceiveStatus.NOT_ELIGIBLE, null, request.getCouponId(), "COUPON_INACTIVE");
		}

		String bucket = now.format(MINUTE_BUCKET);
		String limitKey = PromotionRedisKeys.receiveLimit(request.getUserId(), bucket);
		Long cnt = redisTemplate.opsForValue().increment(limitKey);
		if (cnt != null && cnt == 1L) {
			safeExpire(limitKey, PromotionRedisKeys.receiveLimitTtl());
		}
		if (cnt != null && cnt > userLimitPerMinute) {
			return CouponReceiveResponse.of(CouponReceiveStatus.NOT_ELIGIBLE, null, request.getCouponId(), "RATE_LIMITED");
		}

		CouponReceiveTaskEntity task = new CouponReceiveTaskEntity();
		task.setId(UUID.randomUUID());
		task.setUserId(request.getUserId());
		task.setCouponId(couponId);
		task.setIdempotencyKey(idempotencyKey);
		task.setRequestHash(requestHash);
		task.setStatus("NEW");
		task.setCreatedAt(now);
		task.setUpdatedAt(now);
		receiveTaskRepository.save(task);

		Long remaining = decrementStockWithInit(couponId.toString(), coupon.getTotalStock() - coupon.getUsedStock());
		if (remaining == null || remaining < 0L) {
			receiveTaskRepository.updateStatusAndError(task.getId(), "FAILED", "SOLD_OUT", LocalDateTime.now());
			return CouponReceiveResponse.of(CouponReceiveStatus.SOLD_OUT, task.getId().toString(), request.getCouponId(), "SOLD_OUT");
		}

		return CouponReceiveResponse.of(CouponReceiveStatus.PENDING, task.getId().toString(), request.getCouponId(), "PENDING");
	}

	@Transactional(readOnly = true)
	public UserCouponListResponse listByUser(String userId) {
		List<UserCouponEntity> items = userCouponRepository.findByUserId(userId);
		List<UserCouponView> views = new ArrayList<>();
		for (UserCouponEntity uc : items) {
			UserCouponView v = new UserCouponView();
			v.setUserCouponId(uc.getId().toString());
			v.setCouponId(uc.getCouponId().toString());
			v.setCouponNo(uc.getCouponNo());
			v.setUseStatus(uc.getUseStatus());
			v.setReceiveAtEpochMs(toEpochMs(uc.getReceiveTime()));
			v.setUsedOrderNo(uc.getUsedOrderNo());
			v.setUsedAtEpochMs(toEpochMs(uc.getUsedTime()));
			views.add(v);
		}
		return UserCouponListResponse.of(userId, views);
	}

	private Long decrementStockWithInit(String couponId, long initial) {
		Long remaining = stockLuaExecutor.decrementStock(couponId, 1);
		if (remaining != null && remaining >= 0L) {
			return remaining;
		}
		String k = PromotionRedisKeys.stock(couponId);
		String existing = redisTemplate.opsForValue().get(k);
		if (existing == null) {
			long init = Math.max(0L, initial);
			safeSetIfAbsent(k, String.valueOf(init));
			return stockLuaExecutor.decrementStock(couponId, 1);
		}
		return remaining;
	}

	private void safeExpire(String key, java.time.Duration ttl) {
		try {
			redisTemplate.expire(key, ttl);
		} catch (Exception ignored) {
		}
	}

	private void safeSetIfAbsent(String key, String value) {
		try {
			redisTemplate.opsForValue().setIfAbsent(key, value);
		} catch (Exception ignored) {
		}
	}

	private UUID parseUuid(String s) {
		try {
			return UUID.fromString(s);
		} catch (Exception e) {
			return null;
		}
	}

	private String sha256(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(dig);
		} catch (Exception e) {
			return "";
		}
	}

	private Long toEpochMs(LocalDateTime t) {
		if (t == null) {
			return null;
		}
		return t.toInstant(ZoneOffset.UTC).toEpochMilli();
	}
}
