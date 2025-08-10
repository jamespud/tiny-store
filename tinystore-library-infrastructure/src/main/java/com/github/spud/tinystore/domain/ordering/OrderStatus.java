package com.github.spud.tinystore.domain.ordering;

/**
 * 订单状态枚举
 * 对应 order_status 类型
 */
public enum OrderStatus {
    PENDING("待确认"),
    WAIT_PAYMENT("待支付"),
    PAID("已支付"),
    PROCESSING("处理中"),
    ALLOCATED("已分配"),
    SHIPPED("已发货"),
    DELIVERED("已送达"),
    COMPLETED("已完成"),
    CANCELED("已取消"),
    CLOSED("已关闭");
    
    private final String description;
    
    OrderStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
