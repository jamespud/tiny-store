package com.github.spud.tinystore.promotion.infrastructure.redis;

import java.time.Duration;

public final class PromotionRedisKeys {

	private static final String STOCK_KEY_PATTERN = "coupon:stock:%s";
	private static final String RECEIVE_LIMIT_KEY_PATTERN = "user:coupon:receive:limit:%s:%s";
	private static final String LOCK_CACHE_KEY_PATTERN = "coupon:lock:%s";
	private static final Duration RECEIVE_LIMIT_TTL = Duration.ofMinutes(1);

	private PromotionRedisKeys() {
	}

	public static String stock(String couponId) {
		return String.format(STOCK_KEY_PATTERN, couponId);
	}

	public static String receiveLimit(String userId, String minuteBucket) {
		return String.format(RECEIVE_LIMIT_KEY_PATTERN, userId, minuteBucket);
	}

	public static Duration receiveLimitTtl() {
		return RECEIVE_LIMIT_TTL;
	}

	public static String lockCache(String lockId) {
		return String.format(LOCK_CACHE_KEY_PATTERN, lockId);
	}
}
