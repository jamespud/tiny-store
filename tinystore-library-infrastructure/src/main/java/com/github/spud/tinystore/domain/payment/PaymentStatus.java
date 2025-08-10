package com.github.spud.tinystore.domain.payment;

/**
 * 支付状态枚举
 * 对应 payment_status 类型
 */
public enum PaymentStatus {
    INIT("初始化"),
    PENDING("待支付"),
    SUCCESS("支付成功"),
    FAILED("支付失败"),
    CLOSED("已关闭"),
    REFUNDED("已退款");
    
    private final String description;
    
    PaymentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
