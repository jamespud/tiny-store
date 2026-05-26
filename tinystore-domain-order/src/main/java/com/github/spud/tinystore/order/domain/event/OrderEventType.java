package com.github.spud.tinystore.order.domain.event;

/**
 * 订单域事件类型枚举
 */
public enum OrderEventType {
    TRADE_CREATED("TRADE_CREATED", "交易创建"),
    TRADE_PAID("TRADE_PAID", "交易已支付"),
    TRADE_CLOSED("TRADE_CLOSED", "交易关闭"),
    TRADE_SUCCESS("TRADE_SUCCESS", "交易成功"),
    ORDER_CREATED("ORDER_CREATED", "子单创建"),
    ORDER_PAID("ORDER_PAID", "子单已支付"),
    ORDER_CLOSED("ORDER_CLOSED", "子单关闭"),
    ORDER_SUCCESS("ORDER_SUCCESS", "子单完成"),
    INVENTORY_CONFIRM_CONFLICT("INVENTORY_CONFIRM_CONFLICT", "库存确认冲突"),
    PAYMENT_INTENT_CREATED("PAYMENT_INTENT_CREATED", "支付意图创建"),
    PACKAGE_SHIPPED("PACKAGE_SHIPPED", "包裹已发货"),
    PACKAGE_DELIVERED("PACKAGE_DELIVERED", "包裹已签收"),
    AFTER_SALE_APPLIED("AFTER_SALE_APPLIED", "售后申请"),
    AFTER_SALE_APPROVED("AFTER_SALE_APPROVED", "售后已批准"),
    REFUND_REQUESTED("REFUND_REQUESTED", "退款请求"),
    REFUND_SUCCEEDED("REFUND_SUCCEEDED", "退款成功");

    private final String code;
    private final String displayName;

    OrderEventType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
