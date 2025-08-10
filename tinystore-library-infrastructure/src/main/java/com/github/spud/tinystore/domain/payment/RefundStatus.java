package com.github.spud.tinystore.domain.payment;

/**
 * 退款状态枚举
 * 对应 refund_status 类型
 */
public enum RefundStatus {
    REQUESTED("已申请"),
    APPROVED("已审核"),
    REJECTED("已拒绝"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("处理失败");
    
    private final String description;
    
    RefundStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
