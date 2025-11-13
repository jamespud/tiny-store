package com.github.spud.tinystore.order.domain.statemachine.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 售后明细状态（AFTER_SALE 细分）
 */
public enum AfterSaleStatus {
	NONE("NONE", "无售后"),
	
	CREATED("CREATED", "售后已创建"),
	
	REVIEW_APPROVED("REVIEW_APPROVED", "审核通过"),
	
	REVIEW_REJECTED("REVIEW_REJECTED", "审核拒绝"),
	
	RETURNED("RETURNED", "已退货"),
	
	AFTERSALE_RECEIVED("AFTERSALE_RECEIVED", "售后已确认收货"),
	
	// 售后收货不对
	AFTERSALE_REJECTED("AFTERSALE_REJECTED", "售后拒绝收货"),
	
	COMPLETED("COMPLETED", "售后完成"),
	
	CLOSED("CLOSED", "售后关闭");

	private final String code;
	private final String label;

	AfterSaleStatus(String code, String label) {
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
