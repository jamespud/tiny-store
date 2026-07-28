package com.github.spud.tinystore.inventory.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReconciliationSnapshot Derived Flag Tests")
class ReconciliationSnapshotTest {

    /** All-fields helper. Redis fields null when key absent. */
    private ReconciliationSnapshot snap(long dbTotal, long dbConfirmed, long dbPreDeducted,
                                        Long redisTotal, Long redisDeducted) {
        return ReconciliationSnapshot.builder()
                .shopId("shop").skuId("sku")
                .dbTotalQuantity(dbTotal)
                .dbConfirmedQuantity(dbConfirmed)
                .dbPreDeductedQuantity(dbPreDeducted)
                .redisTotal(redisTotal)
                .redisDeducted(redisDeducted)
                .build();
    }

    @Test
    @DisplayName("targets_includeConfirmed")
    void targets_includeConfirmed() {
        ReconciliationSnapshot s = snap(95, 5, 0, null, null);
        // DB total=95 (100 initial - 5 confirmed), confirmed=5 -> Redis total target = 100
        assertThat(s.getTargetTotal()).isEqualTo(100);
        // DB preDeducted=0, confirmed=5 -> Redis deducted target = 5
        assertThat(s.getTargetDeducted()).isEqualTo(5);
    }

    @Test
    @DisplayName("confirmedPresent_correctRedisValues_areInSync_noFalseDesync")
    void confirmedPresent_correctRedisValues_areInSync_noFalseDesync() {
        // After confirming 5 of 100: DB total=95, confirmed=5, preDeducted=0.
        // Redis total=100 (unchanged by confirm), deducted=5 (confirm does not decrement).
        // This is the CORRECT in-sync state. Old logic falsely flagged desync (100!=95, 5>0).
        ReconciliationSnapshot s = snap(95, 5, 0, 100L, 5L);
        assertThat(s.isRedisInSync()).isTrue();
        assertThat(s.isTotalTooHigh()).isFalse();
        assertThat(s.isTotalTooLow()).isFalse();
        assertThat(s.isDeductedTooLow()).isFalse();
        assertThat(s.isDeductedTooHigh()).isFalse();
        assertThat(s.isOversellRisk()).isFalse();
        assertThat(s.isLostSalesRisk()).isFalse();
    }

    @Test
    @DisplayName("totalTooHigh_flagsOversellRisk")
    void totalTooHigh_flagsOversellRisk() {
        // targetTotal=100, redisTotal=150 -> cap too high -> oversell risk
        ReconciliationSnapshot s = snap(100, 0, 0, 150L, null);
        assertThat(s.isTotalTooHigh()).isTrue();
        assertThat(s.isOversellRisk()).isTrue();
        assertThat(s.isLostSalesRisk()).isFalse();
    }

    @Test
    @DisplayName("totalTooLow_flagsLostSalesRisk")
    void totalTooLow_flagsLostSalesRisk() {
        // targetTotal=100, redisTotal=80 -> cap too low -> lost-sales risk
        ReconciliationSnapshot s = snap(100, 0, 0, 80L, null);
        assertThat(s.isTotalTooLow()).isTrue();
        assertThat(s.isLostSalesRisk()).isTrue();
        assertThat(s.isOversellRisk()).isFalse();
    }

    @Test
    @DisplayName("deductedTooLow_flagsOversellRisk")
    void deductedTooLow_flagsOversellRisk() {
        // targetDeducted=5, redisDeducted=2 -> admission count too low -> available looks too high -> oversell
        ReconciliationSnapshot s = snap(100, 0, 5, 100L, 2L);
        assertThat(s.isDeductedTooLow()).isTrue();
        assertThat(s.isOversellRisk()).isTrue();
    }

    @Test
    @DisplayName("deductedTooHigh_flagsLostSalesRisk")
    void deductedTooHigh_flagsLostSalesRisk() {
        // targetDeducted=2, redisDeducted=9 -> over-counted -> over-rejecting -> lost-sales
        ReconciliationSnapshot s = snap(100, 0, 2, 100L, 9L);
        assertThat(s.isDeductedTooHigh()).isTrue();
        assertThat(s.isLostSalesRisk()).isTrue();
    }

    @Test
    @DisplayName("isDbOversold_true_whenDbTotalNegative")
    void isDbOversold_true_whenDbTotalNegative() {
        assertThat(snap(-1, 0, 0, null, null).isDbOversold()).isTrue();
        assertThat(snap(0, 0, 0, null, null).isDbOversold()).isFalse();
    }

    @Test
    @DisplayName("isNegativeAvailable_true_whenRedisTotalMinusDeductedNegative")
    void isNegativeAvailable_true_whenRedisTotalMinusDeductedNegative() {
        assertThat(snap(100, 0, 0, 5L, 10L).isNegativeAvailable()).isTrue();
        assertThat(snap(100, 0, 0, 10L, 5L).isNegativeAvailable()).isFalse();
    }

    @Test
    @DisplayName("redisAbsentKeys_notTreatedAsDesync")
    void redisAbsentKeys_notTreatedAsDesync() {
        // Redis keys never initialized (null) -> cannot determine desync -> not flagged
        ReconciliationSnapshot s = snap(100, 5, 3, null, null);
        assertThat(s.isTotalTooHigh()).isFalse();
        assertThat(s.isTotalTooLow()).isFalse();
        assertThat(s.isDeductedTooLow()).isFalse();
        assertThat(s.isDeductedTooHigh()).isFalse();
    }

    @Test
    @DisplayName("clean_whenAllInSync_includingConfirmed")
    void clean_whenAllInSync_includingConfirmed() {
        // Reserve 3 of 100 then confirm 3: DB total=97, confirmed=3, preDeducted=0;
        // Redis total=100, deducted=3.
        ReconciliationSnapshot s = snap(97, 3, 0, 100L, 3L);
        assertThat(s.isRedisInSync()).isTrue();
        assertThat(s.isOversellRisk()).isFalse();
        assertThat(s.isLostSalesRisk()).isFalse();
        assertThat(s.isNegativeAvailable()).isFalse();
    }
}
