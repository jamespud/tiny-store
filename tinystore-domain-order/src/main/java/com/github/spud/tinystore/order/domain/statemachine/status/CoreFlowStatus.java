package com.github.spud.tinystore.order.domain.statemachine.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 核心订单主生命周期状态（CORE_FLOW） 终态集合: COMPLETED, CANCELLED, CLOSED, REFUNDED
 */
public enum CoreFlowStatus {
	CREATED("CREATED", "已创建"),

	PENDING_PAYMENT("PENDING_PAYMENT", "待支付"),

	PAID("PAID", "已支付"),

	ACCEPTED("ACCEPTED", "已接单"),

	FULFILLING("FULFILLING", "履约中"),

	AFTER_SALE("AFTER_SALE", "售后中"),

	COMPLETED("COMPLETED", "已完成"),

	CANCELLED("CANCELLED", "已取消");

	private final String code;
	private final String label;

	CoreFlowStatus(String code, String label) {
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
