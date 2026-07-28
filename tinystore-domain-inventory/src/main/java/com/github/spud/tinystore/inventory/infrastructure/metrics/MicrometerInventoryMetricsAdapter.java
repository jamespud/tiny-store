package com.github.spud.tinystore.inventory.infrastructure.metrics;

import com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer implementation of InventoryMetricsPort. 8 counters named
 * tinystore.inventory.<event>.total (aligns with order domain convention).
 */
@Component
public class MicrometerInventoryMetricsAdapter implements InventoryMetricsPort {

    private final Counter reserveSuccess;
    private final Counter reserveFail;
    private final Counter confirmConflict;
    private final Counter redisRollbackFail;
    private final Counter expireCount;
    private final Counter adjustCount;
    private final Counter reconcileRepair;
    private final Counter reconcileAlert;

    public MicrometerInventoryMetricsAdapter(MeterRegistry registry) {
        this.reserveSuccess = Counter.builder("tinystore.inventory.reserve.success.total").register(registry);
        this.reserveFail = Counter.builder("tinystore.inventory.reserve.fail.total").register(registry);
        this.confirmConflict = Counter.builder("tinystore.inventory.confirm.conflict.total").register(registry);
        this.redisRollbackFail = Counter.builder("tinystore.inventory.redis.rollback.fail.total").register(registry);
        this.expireCount = Counter.builder("tinystore.inventory.expire.total").register(registry);
        this.adjustCount = Counter.builder("tinystore.inventory.adjust.total").register(registry);
        this.reconcileRepair = Counter.builder("tinystore.inventory.reconcile.repair.total").register(registry);
        this.reconcileAlert = Counter.builder("tinystore.inventory.reconcile.alert.total").register(registry);
    }

    @Override public void reserveSuccess() { reserveSuccess.increment(); }
    @Override public void reserveFail() { reserveFail.increment(); }
    @Override public void confirmConflict() { confirmConflict.increment(); }
    @Override public void redisRollbackFailed() { redisRollbackFail.increment(); }
    @Override public void expired(int count) { expireCount.increment(count); }
    @Override public void adjustSuccess() { adjustCount.increment(); }
    @Override public void reconcileRepaired() { reconcileRepair.increment(); }
    @Override public void reconcileAlert() { reconcileAlert.increment(); }
}
