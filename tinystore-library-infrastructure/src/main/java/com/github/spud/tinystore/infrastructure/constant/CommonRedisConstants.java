package com.github.spud.tinystore.infrastructure.constant;

/**
 * @author Spud
 * @date 2025/8/10
 */
public class CommonRedisConstants {

	// idempotent:{domain}:{service_id}:{api_id}:{unique_key}
	// idempotent:tinystore:order:create:userId=123_orderNum=999 -> deviceId=abc
	public static String IDEMPOTENT_KEY_PREFIX = "idempotent:";


}
