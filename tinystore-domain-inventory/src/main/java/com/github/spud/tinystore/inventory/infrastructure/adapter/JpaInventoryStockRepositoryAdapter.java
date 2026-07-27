package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * JPA adapter implementing the authoritative InventoryStockRepository domain port.
 * <p>
 * Only CONFIRMED transitions may call deductConfirmed().
 * restoreAdmission() is a no-op in the current model because total_quantity is not
 * incremented at reserve time; only the Redis admission count is managed.
 */
@Slf4j
@Repository
public class JpaInventoryStockRepositoryAdapter implements InventoryStockRepository {

    private final JpaInventoryStockRepository jpaRepo;

    public JpaInventoryStockRepositoryAdapter(JpaInventoryStockRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void deductConfirmed(String shopId, String skuId, int quantity) {
        InventoryStockEntity stock = jpaRepo.findByShopIdAndSkuIdForUpdate(shopId, skuId)
                .orElseThrow(() -> new IllegalStateException(
                        "Stock record not found: shopId=" + shopId + ", skuId=" + skuId));

        if (stock.getTotalQuantity() < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for confirmed deduction: shopId=" + shopId +
                    ", skuId=" + skuId + ", available=" + stock.getTotalQuantity() +
                    ", requested=" + quantity);
        }

        stock.setTotalQuantity(stock.getTotalQuantity() - quantity);
        jpaRepo.save(stock);
        log.debug("Deducted confirmed stock: shopId={}, skuId={}, qty={}, remaining={}",
                shopId, skuId, quantity, stock.getTotalQuantity());
    }

    @Override
    public void restoreAdmission(String shopId, String skuId, int quantity) {
        // PRE_DEDUCTED → RELEASED/EXPIRED does NOT change total_quantity.
        // Redis admission count restoration is handled by InventoryDeductGateway.rollback().
        // This method is intentionally a no-op for the current architecture.
        log.debug("restoreAdmission no-op (Redis-managed): shopId={}, skuId={}, qty={}",
                shopId, skuId, quantity);
    }

    @Override
    public void adjustTotal(String shopId, String skuId, long delta) {
        // Replaced in Task 5
        throw new UnsupportedOperationException("adjustTotal not yet implemented (Task 5)");
    }
}
