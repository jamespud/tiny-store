package com.github.spud.tinystore.inventory.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReconciliationSnapshot Derived Flag Tests")
class ReconciliationSnapshotTest {

    private ReconciliationSnapshot snap(long dbTotal, long dbPre, Long redisTotal, Long redisDeducted) {
        return ReconciliationSnapshot.builder()
                .shopId("shop").skuId("sku")
                .dbTotalQuantity(dbTotal)
                .dbConfirmedQuantity(0)
                .dbPreDeductedQuantity(dbPre)
                .redisTotal(redisTotal)
                .redisDeducted(redisDeducted)
                .build();
    }

    @Test
    @DisplayName("isDbOversold_true_whenDbTotalNegative")
    void isDbOversold_true_whenDbTotalNegative() {
        assertThat(snap(-1, 0, null, null).isDbOversold()).isTrue();
        assertThat(snap(0, 0, null, null).isDbOversold()).isFalse();
    }

    @Test
    @DisplayName("isTotalDesync_true_whenRedisTotalDiffersFromDb")
    void isTotalDesync_true_whenRedisTotalDiffersFromDb() {
        assertThat(snap(100, 0, 150L, null).isTotalDesync()).isTrue();
        assertThat(snap(100, 0, 100L, null).isTotalDesync()).isFalse();
        assertThat(snap(100, 0, null, null).isTotalDesync()).isFalse(); // key absent -> not desync
    }

    @Test
    @DisplayName("isAdmissionUnderCounted_true_whenRedisDeductedLessThanDbPreDeducted_oversellRisk")
    void isAdmissionUnderCounted_true_whenRedisDeductedLessThanDbPreDeducted() {
        assertThat(snap(100, 5, 100L, 2L).isAdmissionUnderCounted()).isTrue();
        assertThat(snap(100, 5, 100L, 5L).isAdmissionUnderCounted()).isFalse();
    }

    @Test
    @DisplayName("isAdmissionOverCounted_true_whenRedisDeductedGreaterThanDbPreDeducted_lostSalesRisk")
    void isAdmissionOverCounted_true_whenRedisDeductedGreaterThanDbPreDeducted() {
        assertThat(snap(100, 2, 100L, 9L).isAdmissionOverCounted()).isTrue();
        assertThat(snap(100, 9, 100L, 9L).isAdmissionOverCounted()).isFalse();
    }

    @Test
    @DisplayName("isNegativeAvailable_true_whenRedisTotalMinusDeductedNegative")
    void isNegativeAvailable_true_whenRedisTotalMinusDeductedNegative() {
        assertThat(snap(100, 0, 5L, 10L).isNegativeAvailable()).isTrue();
        assertThat(snap(100, 0, 10L, 5L).isNegativeAvailable()).isFalse();
    }

    @Test
    @DisplayName("allFlagsFalse_whenCleanAndInSync")
    void allFlagsFalse_whenCleanAndInSync() {
        ReconciliationSnapshot s = snap(100, 5, 100L, 5L);
        assertThat(s.isDbOversold()).isFalse();
        assertThat(s.isTotalDesync()).isFalse();
        assertThat(s.isAdmissionUnderCounted()).isFalse();
        assertThat(s.isAdmissionOverCounted()).isFalse();
        assertThat(s.isNegativeAvailable()).isFalse();
    }
}
