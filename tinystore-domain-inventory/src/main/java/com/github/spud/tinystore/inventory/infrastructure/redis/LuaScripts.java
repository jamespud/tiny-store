package com.github.spud.tinystore.inventory.infrastructure.redis;

/**
 * Lua 脚本占位常量（Phase1 不实际装载执行）。
 */
public final class LuaScripts {

	private LuaScripts() {
	}

	public static final String RESERVE = "-- reserve.lua 占位\n-- 参数: tenantId, skuId, qty, now, expireMillis, reservationId, operationId\nreturn 0";
	public static final String BATCH_AVAILABLE = "-- batch_available.lua 占位\n-- 参数: k1..kn\nreturn {}";
}

