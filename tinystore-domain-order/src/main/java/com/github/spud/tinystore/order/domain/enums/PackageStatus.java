package com.github.spud.tinystore.order.domain.enums;

/**
 * 包裹状态枚举
 */
public enum PackageStatus {
    CREATED("CREATED", "已创建"),
    SHIPPED("SHIPPED", "已发货"),
    DELIVERED("DELIVERED", "已签收");

    private final String code;
    private final String displayName;

    PackageStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PackageStatus getByCode(String code) {
        for (PackageStatus status : PackageStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PackageStatus code: " + code);
    }
}
