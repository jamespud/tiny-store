package com.github.spud.tinystore.order.domain.model.line;

/**
 * 订单行类型枚举
 * 
 * 区分简单行与组合套装的不同类型
 */
public enum LineType {
    /**
     * 简单行：直接对应单个SKU的购买
     */
    SIMPLE,
    
    /**
     * 组合行：套装的总体容器，承载组合价与促销语义
     * 不直接发货，通过关联的组件行实现履约
     */
    COMPOSITE,
    
    /**
     * 组件行：套装中的具体SKU行，承载真实发货与售后
     * 通过bundleGroupId或parentCompositeId与组合行关联
     */
    COMPONENT
}