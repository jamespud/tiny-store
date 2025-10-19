package com.github.spud.tinystore.order.interfaces.error;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 订单业务异常
 *
 * @author Spud
 * @date 2025/8/16
 */
public class OrderBusinessException extends RuntimeException {

	private final OrderErrorCode errorCode;
	private final Map<String, Object> contextMap;

	public OrderBusinessException(OrderErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.contextMap = new HashMap<>();
	}

	public OrderBusinessException(OrderErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.contextMap = new HashMap<>();
	}

	public OrderBusinessException(OrderErrorCode errorCode, Map<String, Object> contextMap) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.contextMap = contextMap;
	}

	public OrderBusinessException(OrderErrorCode errorCode, String message,
		Map<String, Object> contextMap) {
		super(message);
		this.errorCode = errorCode;
		this.contextMap = contextMap;
	}

	public OrderBusinessException(String s, String message, HttpStatus httpStatus) {

	}

	public static Exception riskBlocked(Object reason) {
		return null;
	}

	public static Exception skuUnavailable(@NotNull(message = "SKU ID不能为空") String skuId) {
		return null;
	}

	public static Exception stockInsufficient(Object lackSkuId) {
		return null;
	}


	public OrderErrorCode getErrorCode() {
		return errorCode;
	}

	public Map<String, Object> getContextMap() {
		return contextMap;
	}

}
