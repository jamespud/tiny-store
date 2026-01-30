package com.github.spud.tinystore.order.domain.enums;

/**
 * 支付子状态枚举
 */
public enum PayStatus {
    UNPAID("UNPAID", "未支付"),
    PAID("PAID", "已支付"),
    PART_REFUNDED("PART_REFUNDED", "部分退款"),
    REFUNDED("REFUNDED", "已全额退款");

    private final String code;
    private final String displayName;

    PayStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PayStatus getByCode(String code) {
        for (PayStatus status : PayStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PayStatus code: " + code);
    }
}
