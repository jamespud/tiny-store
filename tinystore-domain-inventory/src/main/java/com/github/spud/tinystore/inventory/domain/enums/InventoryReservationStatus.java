package com.github.spud.tinystore.inventory.domain.enums;

/**
 * Canonical inventory reservation status vocabulary.
 * <p>
 * Only these four values are allowed to be written in new code.
 * Legacy values (RESERVED, COMMITTED) are migration-read-only and must NOT appear in new writes.
 */
public enum InventoryReservationStatus {

    /**
     * Redis admission approved, DB reservation written. Awaiting payment confirmation.
     */
    PRE_DEDUCTED("PRE_DEDUCTED"),

    /**
     * Payment succeeded. Inventory finally deducted. Terminal state. Irreversible.
     */
    CONFIRMED("CONFIRMED"),

    /**
     * Reservation released (order cancelled or manually released). Terminal state.
     * Inventory admission count has been restored.
     */
    RELEASED("RELEASED"),

    /**
     * Reservation expired by the Inventory expiry scheduler. Terminal state.
     * Produced only by Inventory domain internals; Order domain must NOT write this.
     */
    EXPIRED("EXPIRED");

    private final String code;

    InventoryReservationStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * Returns true if this status is a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == CONFIRMED || this == RELEASED || this == EXPIRED;
    }

    /**
     * Parse from DB string value. Supports both canonical and legacy compatibility values.
     * Legacy values (RESERVED, COMMITTED) are mapped to their canonical equivalents.
     *
     * @throws IllegalArgumentException for unknown values
     */
    public static InventoryReservationStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("InventoryReservationStatus code must not be null");
        }
        return switch (code) {
            case "PRE_DEDUCTED" -> PRE_DEDUCTED;
            case "CONFIRMED" -> CONFIRMED;
            case "RELEASED" -> RELEASED;
            case "EXPIRED" -> EXPIRED;
            // Legacy compatibility mappings (read-only, never write these back)
            case "RESERVED" -> PRE_DEDUCTED;
            case "COMMITTED" -> CONFIRMED;
            default -> throw new IllegalArgumentException("Unknown InventoryReservationStatus code: " + code);
        };
    }
}
