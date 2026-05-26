package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryDeductRecordEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryDeductRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 扣减流水仓储（基础设施层适配器）
 * <p>
 * <strong>架构约束（RFC-001）</strong>：此适配器只能写 DEDUCTED 和 RELEASED 两种执行日志状态。
 * 禁止为 confirm 操作新增 CONFIRMED 状态写入。
 * inventory_deduct_record 是执行审计日志，不承载库存生命周期状态机的裁决逻辑。
 */
@Slf4j
@Component
public class JpaInventoryDeductRecordRepositoryAdapter implements InventoryDeductRecordRepository {

    private final JpaInventoryDeductRecordRepository jpaRepo;

    public JpaInventoryDeductRecordRepositoryAdapter(JpaInventoryDeductRecordRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional
    public void saveDeducted(String orderId, String idempotencyKey,
                             List<OccupyPair> occupyPairs, List<Integer> quantities) {
        for (int i = 0; i < occupyPairs.size(); i++) {
            OccupyPair pair = occupyPairs.get(i);
            InventoryDeductRecordEntity entity = new InventoryDeductRecordEntity()
                    .setOrderId(orderId)
                    .setIdempotencyKey(idempotencyKey)
                    .setShopId(pair.getShopId())
                    .setSkuId(pair.getSkuId())
                    .setOccupyId(pair.getOccupyId())
                    .setQuantity(quantities.get(i))
                    .setStatus("DEDUCTED");
            jpaRepo.save(entity);
        }
    }

    @Override
    @Transactional
    public void markReleased(String orderId, List<OccupyPair> occupyPairs, String reason) {
        for (OccupyPair pair : occupyPairs) {
            int updated = jpaRepo.markReleased(
                    pair.getShopId(), pair.getSkuId(), pair.getOccupyId(),
                    "RELEASED", reason);
            if (updated == 0) {
                log.warn("Deduct record not found or already released: orderId={}, shopId={}, skuId={}, occupyId={}",
                        orderId, pair.getShopId(), pair.getSkuId(), pair.getOccupyId());
            }
        }
    }

    @Override
    @Transactional
    public void saveRedisRollbackFailed(String reservationId, String shopId, String skuId, String reason) {
        InventoryDeductRecordEntity entity = new InventoryDeductRecordEntity()
                .setOrderId(reservationId)
                .setIdempotencyKey("redis-rollback-failed:" + reservationId)
                .setShopId(shopId)
                .setSkuId(skuId)
                .setOccupyId(reservationId)
                .setQuantity(0)
                .setStatus("ROLLBACK_FAILED")
                .setReleaseReason(reason);
        jpaRepo.save(entity);
    }
}
