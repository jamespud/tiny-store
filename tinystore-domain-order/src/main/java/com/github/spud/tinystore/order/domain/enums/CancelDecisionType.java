package com.github.spud.tinystore.order.domain.enums;

/**
 * 订单取消决策类型枚举
 *
 * @author Spud
 * @date 2025/8/28
 */
public enum CancelDecisionType {
	/**
	 * 允许简单取消（未支付订单）
	 */
	ALLOW_SIMPLE,

	/**
	 * 允许取消但需要商家同意
	 */
	NEED_APPROVAL,
	/**
	 * 已支付未发货
	 */
	REFUND_THEN_CANCEL,

	/**
	 * 不允许取消
	 */
	NOT_ALLOWED;
}
