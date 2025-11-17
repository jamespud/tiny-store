package com.github.spud.tinystore.order.interfaces.error;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 订单业务异常
 */
public class OrderBusinessException extends RuntimeException {

	private OrderErrorCode errorCode;
	private final Map<String, Object> contextMap;
	private String customCode;
	private HttpStatus httpStatus;

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
		this.contextMap = contextMap != null ? contextMap : new HashMap<>();
	}

	public OrderBusinessException(OrderErrorCode errorCode, String message,
		Map<String, Object> contextMap) {
		super(message);
		this.errorCode = errorCode;
		this.contextMap = contextMap != null ? contextMap : new HashMap<>();
	}

	/**
	 * 兼容使用自定义字符串错误码与HTTP状态的构造
	 * 示例：new OrderBusinessException("平台优惠券不可用", "ORDER-4005", HttpStatus.BAD_REQUEST)
	 */
	public OrderBusinessException(String message, String customCode, HttpStatus httpStatus) {
		super(message);
		this.errorCode = null;
		this.contextMap = new HashMap<>();
		this.customCode = customCode;
		this.httpStatus = httpStatus;
	}

	// ---- 常用静态工厂 ----
	public static OrderBusinessException riskBlocked(Object reason) {
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("reason", reason);
		OrderBusinessException ex = new OrderBusinessException(OrderErrorCode.ALREADY_PROCESSING,
			"风控校验未通过");
		ex.setHttpStatus(HttpStatus.BAD_REQUEST);
		ex.getContextMap().putAll(ctx);
		return ex;
	}

	public static OrderBusinessException skuUnavailable(@NotNull(message = "SKU ID不能为空") String skuId) {
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("skuId", skuId);
		OrderBusinessException ex = new OrderBusinessException(OrderErrorCode.STOCK_INSUFFICIENT,
			"商品不可售或已下架");
		ex.setHttpStatus(HttpStatus.BAD_REQUEST);
		ex.getContextMap().putAll(ctx);
		return ex;
	}

	public static OrderBusinessException stockInsufficient(Object lackSkuId) {
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("skuId", lackSkuId);
		OrderBusinessException ex = new OrderBusinessException(OrderErrorCode.STOCK_INSUFFICIENT,
			"商品库存不足");
		ex.setHttpStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		ex.getContextMap().putAll(ctx);
		return ex;
	}

	public OrderErrorCode getErrorCode() {
		return errorCode;
	}

	public String getCode() {
		if (customCode != null) return customCode;
		return errorCode != null ? errorCode.getCode() : "ORDER_5001";
	}

	public Map<String, Object> getContextMap() {
		return contextMap;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public void setHttpStatus(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}
}
