package com.github.spud.tinystore.order.domain.statemachine.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 履约细分状态域（FULFILLMENT） 仅在核心状态 AWAITING_FULFILLMENT / FULFILLING / AFTER_SALE(换货再发货阶段) 相关
 */
public enum FulfillmentStatus {
	NONE("NONE", "无"),
	
	CREATED("CREATED", "已创建"),
	
	SHIPPED("SHIPPED", "已发货"),
	
	DELIVERED("DELIVERED", "已妥投"),
	
	RECEIVED("RECEIVED", "已确认收货"),
	
	REJECTED("REJECTED", "已拒收");

	private final String code;
	private final String label;

	FulfillmentStatus(String code, String label) {
		this.code = code;
		this.label = label;
	}

	@JsonValue
	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}
}
