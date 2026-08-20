package com.github.spud.tinystore.inventory.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MicrometerInventoryMetricsAdapter Tests")
class MicrometerInventoryMetricsAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerInventoryMetricsAdapter adapter = new MicrometerInventoryMetricsAdapter(registry);

    @Test
    @DisplayName("reserveSuccess_incrementsReserveSuccessCounter")
    void reserveSuccess_incrementsReserveSuccessCounter() {
        adapter.reserveSuccess();
        assertThat(registry.find("tinystore.inventory.reserve.success.total").counter())
                .as("reserve.success counter").isNotNull();
        assertThat(registry.find("tinystore.inventory.reserve.success.total").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("reserveFail_incrementsReserveFailCounter")
    void reserveFail_incrementsReserveFailCounter() {
        adapter.reserveFail();
        assertThat(registry.find("tinystore.inventory.reserve.fail.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("confirmConflict_incrementsConfirmConflictCounter")
    void confirmConflict_incrementsConfirmConflictCounter() {
        adapter.confirmConflict();
        assertThat(registry.find("tinystore.inventory.confirm.conflict.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("redisRollbackFailed_incrementsCounter")
    void redisRollbackFailed_incrementsCounter() {
        adapter.redisRollbackFailed();
        assertThat(registry.find("tinystore.inventory.redis.rollback.fail.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("expired_incrementsByCount")
    void expired_incrementsByCount() {
        adapter.expired(3);
        assertThat(registry.find("tinystore.inventory.expire.total").counter().count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("adjustSuccess_incrementsAdjustCounter")
    void adjustSuccess_incrementsAdjustCounter() {
        adapter.adjustSuccess();
        assertThat(registry.find("tinystore.inventory.adjust.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("reconcileRepaired_andReconcileAlert_incrementRespectiveCounters")
    void reconcileRepaired_andReconcileAlert_incrementRespectiveCounters() {
        adapter.reconcileRepaired();
        adapter.reconcileAlert();
        assertThat(registry.find("tinystore.inventory.reconcile.repair.total").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("tinystore.inventory.reconcile.alert.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("reconcileLogFailed_incrementsCounter")
    void reconcileLogFailed_incrementsCounter() {
        adapter.reconcileLogFailed();
        assertThat(registry.find("tinystore.inventory.reconcile.log.failed.total").counter().count()).isEqualTo(1.0);
    }
}
