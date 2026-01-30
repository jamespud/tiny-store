package com.github.spud.tinystore.order.domain.enums;

/**
 * 售后状态枚举
 */
public enum AfterSaleStatus {
    APPLIED("APPLIED", "已申请"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已拒绝"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String displayName;

    AfterSaleStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AfterSaleStatus getByCode(String code) {
        for (AfterSaleStatus status : AfterSaleStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AfterSaleStatus code: " + code);
    }
}
