package com.github.spud.tinystore.order.domain.enums;

/**
 * 订单履约子状态枚举
 */
public enum OrderStatus {
    PENDING_PAY("PENDING_PAY", "待付款"),
    PENDING_SHIP("PENDING_SHIP", "待发货"),
    PENDING_RECEIVE("PENDING_RECEIVE", "待收货"),
    SUCCESS("SUCCESS", "交易成功"),
    CLOSED("CLOSED", "交易关闭");

    private final String code;
    private final String displayName;

    OrderStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OrderStatus getByCode(String code) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus code: " + code);
    }
}
