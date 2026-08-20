package com.github.spud.tinystore.inventory.domain.port;

/**
 * Domain port for inventory observability counters (hexagonal: domain depends on this
 * abstraction, not on Micrometer). Implemented in infrastructure by MicrometerInventoryMetricsAdapter.
 */
public interface InventoryMetricsPort {
    void reserveSuccess();
    void reserveFail();
    void confirmConflict();
    void redisRollbackFailed();
    void expired(int count);
    void adjustSuccess();
    void reconcileRepaired();
    void reconcileAlert();
    void reconcileLogFailed();
}
