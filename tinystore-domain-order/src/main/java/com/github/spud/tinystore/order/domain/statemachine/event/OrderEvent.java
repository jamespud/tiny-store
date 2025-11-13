package com.github.spud.tinystore.order.domain.statemachine.event;

/**
 * 订单状态机事件枚举 定义触发状态变迁的各种事件
 *
 * @author Spud
 * @date 2025/9/29
 */
public enum OrderEvent {

	// Payment
	PAYMENT_SUCCEEDED("PAYMENT_SUCCEEDED", "支付成功"),
	PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),
	PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", "支付超时"),

	// Merchant / Acceptance
	MERCHANT_ACCEPTED("MERCHANT_ACCEPTED", "商家接单"),
	MERCHANT_CANCELLED("MERCHANT_CANCELLED", "商家取消订单"),

	// Fulfillment / Logistics
	FULFILLMENT_STARTED("FULFILLMENT_STARTED", "履约开始"),
	GOODS_SHIPPED("GOODS_SHIPPED", "商品已发货"),
	GOODS_DELIVERED("GOODS_DELIVERED", "商品已妥投"),
	GOODS_RECEIVED("GOODS_RECEIVED", "商品已收货"),
	GOODS_REJECTED("GOODS_REJECTED", "商品拒收"),
	FULFILLMENT_TIMEOUT("FULFILLMENT_TIMEOUT", "履约超时"),

	// Completion / Auto
	AUTO_RECEIVE_TIMEOUT("AUTO_RECEIVE_TIMEOUT", "自动确认收货超时"),

	// Cancellation
	USER_CANCELLED("USER_CANCELLED", "用户取消订单"),
	SYSTEM_CANCELLED("SYSTEM_CANCELLED", "系统取消订单");


	private final String code;
	private final String label;

	OrderEvent(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public static OrderEvent fromCode(String code) {
		for (OrderEvent event : values()) {
			if (event.code.equals(code)) {
				return event;
			}
		}
		throw new IllegalArgumentException("Unknown OrderEvent code: " + code);
	}
}