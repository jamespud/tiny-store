package com.github.spud.tinystore.inventory.infrastructure.redis;

/**
 * Redis Key 生成工具
 */
public final class RedisKeys {

	private RedisKeys() {
	}

	public static String opKey(String operationId) {
		return "stock:op:" + operationId;
	}

	public static String reservationTtlKey(String reservationId) {
		return "stock:reservation:ttl:" + reservationId;
	}
}

