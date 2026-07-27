package com.github.spud.tinystore.inventory.domain.port;

import com.github.spud.tinystore.inventory.domain.value.ReconciliationSnapshot;

/**
 * Read-only reconciliation port. Detects oversell / over-deduction / desync.
 * Does NOT repair (repair is the job of Priority 2 InventoryReconcileJob).
 */
public interface InventoryReconciliationPort {
    ReconciliationSnapshot snapshot(String shopId, String skuId);
}
