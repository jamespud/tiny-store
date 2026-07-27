package com.github.spud.tinystore.inventory.domain.port;

/**
 * Domain port for authoritative inventory stock ledger.
 * <p>
 * Only CONFIRMED transitions may deduct total_quantity.
 */
public interface InventoryStockRepository {

    /**
     * Deduct confirmed quantity from total_quantity with a pessimistic write lock.
     * Must be called within the same transaction as the reservation CONFIRMED transition.
     *
     * @param shopId   shop ID
     * @param skuId    SKU ID
     * @param quantity quantity to deduct (positive)
     * @throws IllegalStateException if stock is insufficient
     */
    void deductConfirmed(String shopId, String skuId, int quantity);

    /**
     * Restore total_quantity when a PRE_DEDUCTED reservation is RELEASED or EXPIRED.
     * <p>
     * Note: For PRE_DEDUCTED, total_quantity was NOT decremented at reserve time,
     * so this only restores the Redis admission count (handled by gateway).
     * This method is intentionally a no-op for pure PRE_DEDUCTED → RELEASED/EXPIRED paths
     * but is kept for future ledger snapshot compatibility.
     */
    void restoreAdmission(String shopId, String skuId, int quantity);

    /**
     * Adjust total_quantity by a signed delta (Canonical Adjustment pillar).
     * delta > 0: total_quantity += delta (refund / replenish).
     * delta < 0: total_quantity -= |delta|; CHECK(total>=0) backs this up;
     *            throw IllegalStateException if insufficient.
     * Pessimistic lock (findByShopIdAndSkuIdForUpdate) is used inside the adapter.
     */
    void adjustTotal(String shopId, String skuId, long delta);
}
