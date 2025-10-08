package com.github.spud.tinystore.order.infrastructure.statemachine.enums;

/**
 * 订单主状态枚举
 * 定义订单的主要流转状态
 *
 * @author Spud
 * @date 2025/9/29
 */
public enum OrderMainStatus {

	/**
	 * 待支付
	 */
	PENDING_PAYMENT("PENDING_PAYMENT", "待支付"),

	/**
	 * 已支付
	 */
	PAID("PAID", "已支付"),

	/**
	 * 履约中
	 */
	FULFILLING("FULFILLING", "履约中"),

	/**
	 * 已完成
	 */
	COMPLETED("COMPLETED", "已完成"),

	/**
	 * 已取消
	 */
	CANCELLED("CANCELLED", "已取消"),

	/**
	 * 售后中
	 */
	AFTER_SALE("AFTER_SALE", "售后中");

	private final String code;
	private final String label;

	OrderMainStatus(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public static OrderMainStatus fromCode(String code) {
		for (OrderMainStatus status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown MainOrderStatus code: " + code);
	}
}