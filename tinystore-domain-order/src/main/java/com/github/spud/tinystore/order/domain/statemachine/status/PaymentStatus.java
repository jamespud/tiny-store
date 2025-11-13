package com.github.spud.tinystore.order.domain.statemachine.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 支付 & 退款状态域（PAYMENT）
 */
public enum PaymentStatus {
	NONE("NONE", "无"),
	
	CREATED("CREATED", "已创建"),
	
	PAID("PAID", "已支付"),
	
	REFUND_SUCCESS("REFUND_SUCCESS", "退款成功"),
	
	REFUND_FAILED("REFUND_FAILED", "退款失败");

	private final String code;
	private final String label;

	PaymentStatus(String code, String label) {
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