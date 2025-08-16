package com.github.spud.tinystore.order.constant;

/**
 * @author Spud
 * @date 2025/8/16
 */
public enum OrderStatus {

	PENDING("pending"), // 订单待处理状态
	PROCESSING("processing"), // 订单处理中状态
	COMPLETED("completed"), // 订单已完成状态
	CANCELED("canceled"), // 订单已取消状态
	PAYMENT_PENDING("payment_pending"), // 订单待支付状态
	PAYMENT_FAILED("payment_failed"), // 订单支付失败状态
	SHIPPED("shipped"), // 订单已发货状态
	DELIVERED("delivered"), // 订单已送达状态
	RETURNED("returned"), // 订单已退货状态
	REFUNDED("refunded"), // 订单已退款状态
	EXPIRED("expired"), // 订单已过期状态
	PROCESSING_FAILED("processing_failed"), // 订单处理失败状态
	FAILED("failed"); // 订单处理失败状态

	public final String status;

	OrderStatus(String status) {
		this.status = status;
	}
}
