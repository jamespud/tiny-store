package com.github.spud.tinystore.order.domain.enums;

/**
 * 售后类型枚举
 */
public enum AfterSaleType {
    REFUND_ONLY("REFUND_ONLY", "仅退款"),
    RETURN_AND_REFUND("RETURN_AND_REFUND", "退货退款"),
    EXCHANGE("EXCHANGE", "换货");

    private final String code;
    private final String displayName;

    AfterSaleType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AfterSaleType getByCode(String code) {
        for (AfterSaleType type : AfterSaleType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AfterSaleType code: " + code);
    }
}
