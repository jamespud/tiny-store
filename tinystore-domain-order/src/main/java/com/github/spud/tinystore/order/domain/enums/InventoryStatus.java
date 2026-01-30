package com.github.spud.tinystore.order.domain.enums;

/**
 * 库存子状态枚举
 */
public enum InventoryStatus {
    UNLOCKED("UNLOCKED", "未锁定"),
    LOCKED("LOCKED", "已锁定"),
    DEDUCTED("DEDUCTED", "已扣减"),
    RELEASED("RELEASED", "已释放");

    private final String code;
    private final String displayName;

    InventoryStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InventoryStatus getByCode(String code) {
        for (InventoryStatus status : InventoryStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown InventoryStatus code: " + code);
    }
}
