package com.github.spud.tinystore.order.domain.enums;

/**
 * Inventory projection version for shop orders.
 * <p>
 * version 1 = legacy V2 deduct projection (InventoryOccupyPair, LOCKED/DEDUCTED vocabulary)
 * version 2 = canonical reservation projection (InventoryReservationRef, PRE_DEDUCTED/CONFIRMED vocabulary)
 * <p>
 * All shop_orders within the same trade must use the same version.
 */
public enum InventoryProjectionVersion {

    /**
     * Legacy V2 deduct path.
     * Uses InventoryOccupyPair and old status vocabulary (LOCKED, DEDUCTED).
     * Does NOT perform inventory confirm on payment success.
     */
    VERSION_1(1),

    /**
     * Canonical reservation path (RFC-001).
     * Uses InventoryReservationRef and canonical vocabulary (PRE_DEDUCTED, CONFIRMED).
     * MUST perform inventory confirm on payment success.
     */
    VERSION_2(2);

    private final int value;

    InventoryProjectionVersion(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static InventoryProjectionVersion fromValue(int value) {
        return switch (value) {
            case 1 -> VERSION_1;
            case 2 -> VERSION_2;
            default -> throw new IllegalArgumentException("Unknown InventoryProjectionVersion: " + value);
        };
    }
}
