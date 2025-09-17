package com.github.spud.tinystore.inventory.domain.error;

/**
 * 库存错误码定义（仅枚举常量字符串，不含实现逻辑）
 */
public enum InventoryErrorCode {
	STOCK_NOT_FOUND,
	INSUFFICIENT_STOCK,
	RESERVATION_NOT_FOUND,
	STATE_CONFLICT,
	IDEMPOTENT_HIT,
	INVALID_PARAM
}

