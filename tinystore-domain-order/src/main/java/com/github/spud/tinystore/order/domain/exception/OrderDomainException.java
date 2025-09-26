package com.github.spud.tinystore.order.domain.exception;

/**
 * Base exception for order domain business rule violations
 *
 * @author Spud
 * @date 2025/9/22
 */
public class OrderDomainException extends RuntimeException {

	private final String errorCode;

	public OrderDomainException(String message) {
		super(message);
		this.errorCode = "ORDER_DOMAIN_ERROR";
	}

	public OrderDomainException(String message, String errorCode) {
		super(message);
		this.errorCode = errorCode;
	}

	public OrderDomainException(String message, Throwable cause) {
		super(message, cause);
		this.errorCode = "ORDER_DOMAIN_ERROR";
	}

	public OrderDomainException(String message, String errorCode, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}
}