package com.github.spud.tinystore.inventory.domain.port;

/**
 * Domain port for the inventory_adjustment audit log (Canonical Adjustment pillar).
 * Append-only signed-delta log; carries no lifecycle state.
 */
public interface InventoryAdjustmentRepository {

    boolean existsByReasonAndReferenceIdAndSku(String reason, String referenceId,
                                               String shopId, String skuId);

    void saveAdjustment(String shopId, String skuId, long delta,
                        String reason, String referenceId);
}
