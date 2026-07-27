package com.github.spud.tinystore.inventory.domain.enums;

/**
 * Canonical inventory adjustment reason vocabulary.
 * Only RESTOCK_REFUND has a live caller in this version;
 * MANUAL_ADJUST / REPLENISH / CORRECTION are placeholders for future endpoints.
 */
public enum AdjustmentReason {

    RESTOCK_REFUND("RESTOCK_REFUND"),
    MANUAL_ADJUST("MANUAL_ADJUST"),
    REPLENISH("REPLENISH"),
    CORRECTION("CORRECTION");

    private final String code;

    AdjustmentReason(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AdjustmentReason fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("AdjustmentReason code must not be null");
        }
        for (AdjustmentReason r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown AdjustmentReason code: " + code);
    }
}
