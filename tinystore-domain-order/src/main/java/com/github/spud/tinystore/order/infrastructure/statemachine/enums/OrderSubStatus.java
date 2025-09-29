package com.github.spud.tinystore.order.infrastructure.statemachine.enums;

/**
 * 订单子状态枚举
 * 定义订单在各个主状态下的详细子状态
 *
 * @author Spud
 * @date 2025/9/29
 */
public enum OrderSubStatus {

	// 待支付子状态
	/**
	 * 预创建（订单已生成但未发起支付）
	 */
	PRE_CREATED("PRE_CREATED", "预创建"),

	/**
	 * 支付失败
	 */
	PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),

	// 已支付子状态
	/**
	 * 待发货
	 */
	PENDING_SHIP("PENDING_SHIP", "待发货"),

	/**
	 * 部分发货
	 */
	PARTIALLY_SHIPPED("PARTIALLY_SHIPPED", "部分发货"),

	// 履约中子状态
	/**
	 * 待收货
	 */
	PENDING_RECEIVE("PENDING_RECEIVE", "待收货"),

	/**
	 * 部分收货
	 */
	PARTIALLY_RECEIVED("PARTIALLY_RECEIVED", "部分收货"),

	// 售后中子状态
	/**
	 * 退款待审核
	 */
	REFUND_PENDING("REFUND_PENDING", "退款待审核"),

	/**
	 * 退款处理中
	 */
	REFUND_PROCESSING("REFUND_PROCESSING", "退款处理中"),

	/**
	 * 退货待审核
	 */
	RETURN_PENDING("RETURN_PENDING", "退货待审核"),

	/**
	 * 退货在途
	 */
	RETURN_IN_TRANSIT("RETURN_IN_TRANSIT", "退货在途"),

	// 通用子状态
	/**
	 * 正常完成
	 */
	NORMAL_COMPLETE("NORMAL_COMPLETE", "正常完成"),

	/**
	 * 支付超时
	 */
	PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", "支付超时"),

	/**
	 * 用户取消
	 */
	USER_CANCELLED("USER_CANCELLED", "用户取消");

	private final String code;
	private final String label;

	OrderSubStatus(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public static OrderSubStatus fromCode(String code) {
		for (OrderSubStatus status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown OrderSubStatus code: " + code);
	}
}