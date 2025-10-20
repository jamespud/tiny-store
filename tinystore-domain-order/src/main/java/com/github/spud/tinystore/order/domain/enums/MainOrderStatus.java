package com.github.spud.tinystore.order.domain.enums;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/10/6
 */
// MainOrderStatus.java（主订单状态）
@Getter
public enum MainOrderStatus {
	
	/**
	 * 主订单创建完成（所有子订单同步为已创建）
	 */
	CREATED("合并已创建", 0),
	
	/**
	 * 主订单创建后待支付
	 */
	PAY_PENDING("合并待支付", 10),
	
	/**
	 * 主订单支付成功（所有子订单同步为已支付）
	 */
	PAID("合并已支付", 20),
	
	/**
	 * 主订单取消（所有子订单同步为已取消）
	 */
	CANCELED("合并已取消", 30),
	
	/**
	 * 主订单全额退款（所有子订单同步为已退款）
	 */
	REFUNDED("合并已全额退款", 40);

	private final String desc;
	private final int code;

	MainOrderStatus(String desc, int code) {
		this.desc = desc;
		this.code = code;
	}

}
