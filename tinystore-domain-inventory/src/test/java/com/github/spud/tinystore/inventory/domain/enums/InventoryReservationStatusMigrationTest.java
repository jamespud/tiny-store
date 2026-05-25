package com.github.spud.tinystore.inventory.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that InventoryReservationStatus codes are consistent with the V7 migration SQL.
 * <p>
 * V7 migration (V7__canonicalize_inventory_reservation_status.sql) updates:
 *   RESERVED  → PRE_DEDUCTED
 *   COMMITTED → CONFIRMED
 * <p>
 * This test acts as a compile-time guard to ensure enum codes match the migration values.
 */
@DisplayName("InventoryReservationStatus Migration Consistency Tests")
class InventoryReservationStatusMigrationTest {

    /**
     * Canonical code used in V7 SQL: UPDATE SET status='PRE_DEDUCTED' WHERE status='RESERVED'
     */
    private static final String SQL_PRE_DEDUCTED = "PRE_DEDUCTED";

    /**
     * Canonical code used in V7 SQL: UPDATE SET status='CONFIRMED' WHERE status='COMMITTED'
     */
    private static final String SQL_CONFIRMED = "CONFIRMED";

    /**
     * Legacy codes that V7 migration eliminates from the DB.
     */
    private static final String LEGACY_RESERVED = "RESERVED";
    private static final String LEGACY_COMMITTED = "COMMITTED";

    @Test
    @DisplayName("preDeducted_enumCode_mustMatchV7SqlTargetValue")
    void preDeducted_enumCode_mustMatchV7SqlTargetValue() {
        assertThat(InventoryReservationStatus.PRE_DEDUCTED.getCode())
                .as("PRE_DEDUCTED enum code must match V7 SQL target value")
                .isEqualTo(SQL_PRE_DEDUCTED);
    }

    @Test
    @DisplayName("confirmed_enumCode_mustMatchV7SqlTargetValue")
    void confirmed_enumCode_mustMatchV7SqlTargetValue() {
        assertThat(InventoryReservationStatus.CONFIRMED.getCode())
                .as("CONFIRMED enum code must match V7 SQL target value")
                .isEqualTo(SQL_CONFIRMED);
    }

    @Test
    @DisplayName("legacyReserved_mustMapToPreDeducted_viaFromCode")
    void legacyReserved_mustMapToPreDeducted_viaFromCode() {
        assertThat(InventoryReservationStatus.fromCode(LEGACY_RESERVED))
                .as("Legacy RESERVED code must map to PRE_DEDUCTED for backward compat")
                .isEqualTo(InventoryReservationStatus.PRE_DEDUCTED);
    }

    @Test
    @DisplayName("legacyCommitted_mustMapToConfirmed_viaFromCode")
    void legacyCommitted_mustMapToConfirmed_viaFromCode() {
        assertThat(InventoryReservationStatus.fromCode(LEGACY_COMMITTED))
                .as("Legacy COMMITTED code must map to CONFIRMED for backward compat")
                .isEqualTo(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("released_andExpired_shouldBeTerminalStates")
    void released_andExpired_shouldBeTerminalStates() {
        assertThat(InventoryReservationStatus.RELEASED.isTerminal()).isTrue();
        assertThat(InventoryReservationStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("preDeducted_shouldNotBeTerminal")
    void preDeducted_shouldNotBeTerminal() {
        assertThat(InventoryReservationStatus.PRE_DEDUCTED.isTerminal()).isFalse();
    }
}
