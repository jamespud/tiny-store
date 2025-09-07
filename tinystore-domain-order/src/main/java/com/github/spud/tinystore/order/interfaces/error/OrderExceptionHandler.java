package com.github.spud.tinystore.order.interfaces.error;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 订单异常处理器
 *
 * @author Spud
 * @date 2025/8/16
 */
@Slf4j
@RestControllerAdvice
public class OrderExceptionHandler {

	@ExceptionHandler(OrderBusinessException.class)
	public ResponseEntity<Map<String, Object>> handleOrderBusinessException(
		OrderBusinessException e) {
		String traceId = UUID.randomUUID().toString();

		log.warn("Order business exception traceId={} code={} message={} context={}",
			traceId, e.getErrorCode().getCode(), e.getMessage(), e.getContextMap());

		Map<String, Object> response = new HashMap<>();
		response.put("code", e.getErrorCode().getCode());
		response.put("message", e.getMessage());
		response.put("traceId", traceId);

		// 添加上下文信息
		if (!e.getContextMap().isEmpty()) {
			response.putAll(e.getContextMap());
		}

		HttpStatus status = mapToHttpStatus(e.getErrorCode());
		return ResponseEntity.status(status).body(response);
	}

	private HttpStatus mapToHttpStatus(OrderErrorCode errorCode) {
		return switch (errorCode) {
			case PRICE_CHANGED, STOCK_INSUFFICIENT, ALREADY_PROCESSING -> HttpStatus.CONFLICT;
			case TOKEN_EXPIRED, TOKEN_INVALID, DIGEST_MISMATCH, IDEMPOTENT_REPLAY ->
				HttpStatus.BAD_REQUEST;
			case COUPON_INVALID -> HttpStatus.BAD_REQUEST;
			case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
			case ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case ORDER_STATE_NOT_CANCELABLE -> null;
			case ORDER_ALREADY_CANCELED -> null;
			case ORDER_IDEMPOTENCY_REPLAY -> null;
		};
	}
}
