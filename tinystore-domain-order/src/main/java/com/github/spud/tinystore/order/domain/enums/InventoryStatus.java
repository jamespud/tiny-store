package com.github.spud.tinystore.order.domain.enums;

/**
 * 库存子状态枚举。
 *
 * 新写入仅允许 canonical 词汇；历史词汇仅用于读取兼容。
 */
public enum InventoryStatus {
    UNLOCKED("UNLOCKED", "未锁定"),
    PRE_DEDUCTED("PRE_DEDUCTED", "已预扣"),
    CONFIRMED("CONFIRMED", "已确认"),
    RELEASED("RELEASED", "已释放"),
    EXPIRED("EXPIRED", "已过期");

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
        if (code == null) {
            throw new IllegalArgumentException("InventoryStatus code must not be null");
        }
        return switch (code) {
            case "UNLOCKED" -> UNLOCKED;
            case "PRE_DEDUCTED", "LOCKED", "RESERVED" -> PRE_DEDUCTED;
            case "CONFIRMED", "DEDUCTED", "COMMITTED" -> CONFIRMED;
            case "RELEASED" -> RELEASED;
            case "EXPIRED" -> EXPIRED;
            default -> throw new IllegalArgumentException("Unknown InventoryStatus code: " + code);
        };
    }
}
