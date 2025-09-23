package com.github.spud.tinystore.order.domain.status;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 履约细分状态域（FULFILLMENT）
 * 仅在核心状态 AWAITING_FULFILLMENT / FULFILLING / AFTER_SALE(换货再发货阶段) 相关
 */
public enum FulfillmentStatus {
    NONE("NONE", "无"),
    MERCHANT_PENDING_ACCEPT("MERCHANT_PENDING_ACCEPT", "待商家接单"),
    MERCHANT_ACCEPTED("MERCHANT_ACCEPTED", "商家已接单"),
    READY_TO_SHIP("READY_TO_SHIP", "待发货"),
    OUTBOUND_CONFIRMED("OUTBOUND_CONFIRMED", "已出库"),
    WAITING_PICKUP("WAITING_PICKUP", "待揽收"),
    PICKED_UP("PICKED_UP", "已揽收"),
    IN_TRANSIT("IN_TRANSIT", "运输中"),
    OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "派送中"),
    DELIVERY_PENDING_CONFIRM("DELIVERY_PENDING_CONFIRM", "待签收"),
    DELIVERED("DELIVERED", "已签收"),
    FULFILLMENT_EXCEPTION("FULFILLMENT_EXCEPTION", "履约异常");

    private final String code;
    private final String label;

    FulfillmentStatus(String code, String label) {
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