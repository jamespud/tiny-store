package com.github.spud.tinystore.order.domain.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 售后明细状态（AFTER_SALE 细分）
 */
public enum AfterSaleStatus {
    NONE("NONE", "无售后"),
    APPLY_SUBMITTED("APPLY_SUBMITTED", "售后申请已提交"),
    REVIEWING("REVIEWING", "审核中"),
    REVIEW_APPROVED("REVIEW_APPROVED", "审核通过"),
    REVIEW_REJECTED("REVIEW_REJECTED", "审核拒绝"),
    WAITING_USER_RETURN("WAITING_USER_RETURN", "待用户退货"),
    RETURN_IN_TRANSIT("RETURN_IN_TRANSIT", "退货在途"),
    WAITING_MERCHANT_INSPECTION("WAITING_MERCHANT_INSPECTION", "待商家验货"),
    INSPECTION_APPROVED("INSPECTION_APPROVED", "验货通过"),
    INSPECTION_REJECTED("INSPECTION_REJECTED", "验货拒绝"),
    PROCESSING("PROCESSING", "售后处理中"),
    NEGOTIATION_PENDING("NEGOTIATION_PENDING", "协商中"),
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
