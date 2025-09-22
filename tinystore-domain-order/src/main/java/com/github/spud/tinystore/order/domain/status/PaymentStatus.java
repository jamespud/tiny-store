package com.github.spud.tinystore.order.domain.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 支付 & 退款状态域（PAYMENT）
 */
public enum PaymentStatus {
    NONE("NONE", "无"),
    PAYMENT_PENDING("PAYMENT_PENDING", "待支付"),
    PAYMENT_PROCESSING("PAYMENT_PROCESSING", "支付处理中"),
    PAYMENT_SUCCESS("PAYMENT_SUCCESS", "支付成功"),
    PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),
    DEPOSIT_PAID("DEPOSIT_PAID", "定金已付"),
    FINAL_PAYMENT_PENDING("FINAL_PAYMENT_PENDING", "待付尾款"),
    REFUND_PENDING("REFUND_PENDING", "退款待受理"),
    REFUND_PROCESSING("REFUND_PROCESSING", "退款处理中"),
    REFUND_SUCCESS("REFUND_SUCCESS", "退款成功"),
    REFUND_FAILED("REFUND_FAILED", "退款失败");

    private final String code;
    private final String label;

    PaymentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String getCode() {return code;}
    public String getLabel() {return label;}
}