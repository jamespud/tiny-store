package com.github.spud.tinystore.inventory.domain.error;

/**
 * 业务异常（简单占位，不含复杂逻辑）
 */
public class BusinessException extends RuntimeException {

	private final InventoryErrorCode code;

	public BusinessException(InventoryErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public InventoryErrorCode getCode() {
		return code;
	}
}

