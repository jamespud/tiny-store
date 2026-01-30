package com.github.spud.tinystore.order.domain.enums;

/**
 * 优惠子状态枚举
 */
public enum PromotionStatus {
    UNAPPLIED("UNAPPLIED", "未应用"),
    RESERVED("RESERVED", "已预留"),
    COMMITTED("COMMITTED", "已核销"),
    RELEASED("RELEASED", "已释放");

    private final String code;
    private final String displayName;

    PromotionStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PromotionStatus getByCode(String code) {
        for (PromotionStatus status : PromotionStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PromotionStatus code: " + code);
    }
}
