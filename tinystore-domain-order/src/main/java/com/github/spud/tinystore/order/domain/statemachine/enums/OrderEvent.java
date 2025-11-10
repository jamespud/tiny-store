package com.github.spud.tinystore.order.domain.statemachine.enums;

/**
 * 订单状态机事件枚举 定义触发状态变迁的各种事件
 *
 * @author Spud
 * @date 2025/9/29
 */
public enum OrderEvent {

	/**
	 * 支付成功
	 */
	PAYMENT_SUCCEEDED("PAYMENT_SUCCEEDED", "支付成功"),

	/**
	 * 商家接单
	 */
	MERCHANT_ACCEPTED("MERCHANT_ACCEPTED", "商家接单"),

	/**
	 * 已发货
	 */
	SHIPPED("SHIPPED", "已发货"),

	/**
	 * 已妥投
	 */
	DELIVERED("DELIVERED", "已妥投"),

	/**
	 * 自动完成
	 */
	AUTO_COMPLETE("AUTO_COMPLETE", "自动完成"),

	/**
	 * 取消审批通过
	 */
	CANCEL_APPROVED("CANCEL_APPROVED", "取消审批通过"),

	/**
	 * 申请售后
	 */
	AFTER_SALE_REQUESTED("AFTER_SALE_REQUESTED", "申请售后"),

	/**
	 * 退款成功
	 */
	REFUND_SUCCESS("REFUND_SUCCESS", "退款成功"),

	/**
	 * 支付超时
	 */
	PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", "支付超时"), 
	
	PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),
	
	FULFILLMENT_STARTED("FULFILLMENT_STARTED", "履约开始"),
	
	GOODS_SHIPPED("GOOD_SHIPPED", "商品已发货"), 
	
	GOODS_RECEIVED("GOODS_RECEIVED", "商品已收货"),
	
	AUTO_CONFIRM_TIMEOUT("AUTO_CONFIRM_TIMEOUT", "自动确认收货超时"),
	
	USER_CANCELLED("USER_CANCELLED", "用户取消订单"),
	
	SYSTEM_CANCELLED("SYSTEM_CANCELLED", "系统取消订单"),
	
 MERCHANT_CANCELLED("MERCHANT_CANCELLED", "商家取消订单");


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