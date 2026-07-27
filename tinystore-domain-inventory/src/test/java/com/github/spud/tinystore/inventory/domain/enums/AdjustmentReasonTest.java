package com.github.spud.tinystore.inventory.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AdjustmentReason Tests")
class AdjustmentReasonTest {

    @Test
    @DisplayName("fromCode_roundTrips_allCodes")
    void fromCode_roundTrips_allCodes() {
        for (AdjustmentReason r : AdjustmentReason.values()) {
            assertThat(AdjustmentReason.fromCode(r.getCode())).isEqualTo(r);
        }
    }

    @Test
    @DisplayName("fromCode_unknown_throws")
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> AdjustmentReason.fromCode("NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromCode_null_throws")
    void fromCode_null_throws() {
        assertThatThrownBy(() -> AdjustmentReason.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("restockRefund_isFirstValue_withExpectedCode")
    void restockRefund_isFirstValue_withExpectedCode() {
        assertThat(AdjustmentReason.RESTOCK_REFUND.getCode()).isEqualTo("RESTOCK_REFUND");
    }
}
