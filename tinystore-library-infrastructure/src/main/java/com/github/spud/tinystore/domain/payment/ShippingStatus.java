package com.github.spud.tinystore.domain.payment;

/**
 * 物流状态枚举
 */
public enum ShippingStatus {
    PENDING("待处理"),
    ALLOCATED("已分配"),
    SHIPPED("已发货"),
    DELIVERED("已送达");
    
    private final String description;
    
    ShippingStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
