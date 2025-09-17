package com.github.spud.tinystore.inventory.domain.exception;

/**
 * 领域业务异常
 */
public class InventoryBusinessException extends RuntimeException {

	private final InventoryErrorCode errorCode;

	public InventoryBusinessException(InventoryErrorCode code, String message) {
		super(message);
		this.errorCode = code;
	}

	public InventoryErrorCode getErrorCode() {
		return errorCode;
	}
}

