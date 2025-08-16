package com.github.spud.tinystore.infrastrucutre.constant;

/**
 * @author Spud
 * @date 2025/8/10
 */
public class CommonRedisConstants {

	public static String REDIS_KEY_PREFIX = "tinystore:";
	public static String REDIS_LOCK_PREFIX = "lock:";
	public static String ORDER_SUBMIT_LOCK = REDIS_LOCK_PREFIX + "order:submit:";
}
