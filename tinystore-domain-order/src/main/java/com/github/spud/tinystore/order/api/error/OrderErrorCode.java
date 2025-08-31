package com.github.spud.tinystore.order.api.error;

/**
 * 订单业务错误码
 *
 * @author Spud
 * @date 2025/8/16
 */
public enum OrderErrorCode {

	TOKEN_EXPIRED("ORDER_4001", "预订单令牌已过期"),
	TOKEN_INVALID("ORDER_4002", "预订单令牌无效"),
	DIGEST_MISMATCH("ORDER_4003", "订单摘要校验失败"),
	IDEMPOTENT_REPLAY("ORDER_4004", "幂等键重复提交"),
	PRICE_CHANGED("ORDER_4005", "商品价格已变化"),
	STOCK_INSUFFICIENT("ORDER_4006", "商品库存不足"),
	COUPON_INVALID("ORDER_4007", "优惠券无效或已失效"),
	INTERNAL_ERROR("ORDER_5001", "系统内部错误"),
	ALREADY_PROCESSING("ORDER_4008", "订单正在处理中"),
	ORDER_NOT_FOUND("ORDER_4040", "订单不存在"),
	ORDER_STATE_NOT_CANCELABLE("ORDER_4090", "当前订单状态不允许取消"),
	ORDER_ALREADY_CANCELED("ORDER_4091", "订单已取消"),
	ORDER_IDEMPOTENCY_REPLAY("ORDER_4092", "取消请求重复提交");

	private final String code;
	private final String message;

	OrderErrorCode(String code, String message) {
		this.code = code;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

}
