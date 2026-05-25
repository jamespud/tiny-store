package com.github.spud.tinystore.inventory.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InventoryReservationStatus}.
 * <p>
 * Coverage:
 * - Canonical codes map correctly
 * - Legacy code aliases map to canonical values
 * - Terminal status flags are correct
 * - Unknown code throws IllegalArgumentException
 */
@DisplayName("InventoryReservationStatus Unit Tests")
class InventoryReservationStatusTest {

    @Test
    @DisplayName("preDeducted_shouldHaveCorrectCode")
    void preDeducted_shouldHaveCorrectCode() {
        assertThat(InventoryReservationStatus.PRE_DEDUCTED.getCode()).isEqualTo("PRE_DEDUCTED");
    }

    @Test
    @DisplayName("confirmed_shouldHaveCorrectCode")
    void confirmed_shouldHaveCorrectCode() {
        assertThat(InventoryReservationStatus.CONFIRMED.getCode()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("released_shouldHaveCorrectCode")
    void released_shouldHaveCorrectCode() {
        assertThat(InventoryReservationStatus.RELEASED.getCode()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("expired_shouldHaveCorrectCode")
    void expired_shouldHaveCorrectCode() {
        assertThat(InventoryReservationStatus.EXPIRED.getCode()).isEqualTo("EXPIRED");
    }

    @ParameterizedTest
    @CsvSource({
        "PRE_DEDUCTED, PRE_DEDUCTED",
        "CONFIRMED,    CONFIRMED",
        "RELEASED,     RELEASED",
        "EXPIRED,      EXPIRED"
    })
    @DisplayName("fromCode_canonicalCodes_shouldMapCorrectly")
    void fromCode_canonicalCodes_shouldMapCorrectly(String input, String expected) {
        assertThat(InventoryReservationStatus.fromCode(input).getCode()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "RESERVED,   PRE_DEDUCTED",
        "COMMITTED,  CONFIRMED"
    })
    @DisplayName("fromCode_legacyCodes_shouldMapToCanonicalValues")
    void fromCode_legacyCodes_shouldMapToCanonicalValues(String legacyCode, String expectedCanonical) {
        assertThat(InventoryReservationStatus.fromCode(legacyCode).getCode()).isEqualTo(expectedCanonical);
    }

    @ParameterizedTest
    @CsvSource({
        "CONFIRMED, true",
        "RELEASED,  true",
        "EXPIRED,   true",
        "PRE_DEDUCTED, false"
    })
    @DisplayName("isTerminal_shouldReturnCorrectly")
    void isTerminal_shouldReturnCorrectly(String code, boolean expectedTerminal) {
        assertThat(InventoryReservationStatus.fromCode(code).isTerminal()).isEqualTo(expectedTerminal);
    }

    @Test
    @DisplayName("fromCode_unknownCode_shouldThrowIllegalArgumentException")
    void fromCode_unknownCode_shouldThrowIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> InventoryReservationStatus.fromCode("INVALID_CODE")
        );
    }
}
