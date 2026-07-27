package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryAdjustmentEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryAdjustmentRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInventoryAdjustmentRepositoryAdapter implements InventoryAdjustmentRepository {

    private final JpaInventoryAdjustmentRepository jpaRepo;

    public JpaInventoryAdjustmentRepositoryAdapter(JpaInventoryAdjustmentRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public boolean existsByReasonAndReferenceIdAndSku(String reason, String referenceId,
                                                     String shopId, String skuId) {
        return jpaRepo.existsByReasonAndReferenceIdAndShopIdAndSkuId(reason, referenceId, shopId, skuId);
    }

    @Override
    public void saveAdjustment(String shopId, String skuId, long delta, String reason, String referenceId) {
        jpaRepo.save(new InventoryAdjustmentEntity()
                .setShopId(shopId)
                .setSkuId(skuId)
                .setDeltaTotal(delta)
                .setReason(reason)
                .setReferenceId(referenceId));
    }
}
