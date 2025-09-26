package com.github.spud.tinystore.order.domain.status;

/**
 * 售后案例类型枚举
 * <p>
 * 定义售后申请的基本类型
 */
public enum AfterSaleCaseType {
	/**
	 * 退款：退回商品并退还货款
	 */
	REFUND,

	/**
	 * 换货：退回原商品，重新发货相同或不同商品
	 */
	EXCHANGE
}
