package com.github.spud.tinystore.inventory.domain.constant;

/**
 * Redis Key 常量
 */
public class RedisConstant {

	public static final String LOCK_KEY_PREFIX = "lock:";

	public static final String STOCK_KEY_PREFIX = "stock:";

	public static final String STOCK_TOTAL_KEY_PREFIX = STOCK_KEY_PREFIX + "total:";

	public static final String STOCK_RESERVED_KEY_PREFIX = STOCK_KEY_PREFIX + "reserved:";

	public static final String STOCK_TOTAL_VERSION_KEY_PREFIX = STOCK_TOTAL_KEY_PREFIX + "version:";

	public static final String STOCK_LOCK_KEY_PREFIX = LOCK_KEY_PREFIX + "stock:";

	public static final String STOCK_TOTAL_LOCK_KEY_PREFIX = STOCK_LOCK_KEY_PREFIX + "total:";

	// 预留记录状态 key 前缀
	public static final String RESERVATION_STATE_KEY_PREFIX =
		STOCK_KEY_PREFIX + "reservation:state:"; // reservation:state:{reservationId}
	// 预留记录 TTL 标记（可选）
	public static final String RESERVATION_TTL_KEY_PREFIX = STOCK_KEY_PREFIX + "reservation:ttl:";
	// 幂等 operationId -> reservationId 映射
	public static final String OPERATION_ID_KEY_PREFIX = STOCK_KEY_PREFIX + "op:"; // op:{operationId}

	private RedisConstant() {
	}
}
