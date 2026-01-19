package com.github.spud.tinystore.order.domain.event;

/**
 * 统一事件类型常量，避免分散字符串命名不一致。
 */
public final class OrderEventTypeConstants {
    private OrderEventTypeConstants() {}

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_ACCEPTED = "order.accepted";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_DELIVERED = "order.delivered";
    public static final String ORDER_RECEIVED = "order.received";

    public static final String PAYMENT_SUCCEEDED = "order.payment.succeeded";
    public static final String MERCHANT_ACCEPTED_LIFECYCLE = "order.lifecycle.changed";
    public static final String GOODS_SHIPPED = "order.fulfillment.shipped";
    public static final String GOODS_DELIVERED = "order.fulfillment.delivered";
    public static final String GOODS_RECEIVED = "order.received";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_LIFECYCLE_CHANGED = "order.lifecycle.changed";
    public static final String REFUND_SUCCEEDED = "order.refund.succeeded";
    public static final String AFTERSALE_APPLIED = "order.aftersale.applied";
}
