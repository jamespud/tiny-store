package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryAdjustCommand;
import com.github.spud.tinystore.inventory.domain.port.InventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort;
import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.domain.value.AdjustmentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical inventory adjustment domain service.
 * <p>
 * Owns non-reservation stock mutations (refund restock, manual adjust, replenish, correction).
 * Independent of the reservation lifecycle: it never touches inventory_reservation status.
 * <p>
 * Invariants:
 * <ul>
 *   <li>Idempotency via DB unique constraint (reason, reference_id, shop_id, sku_id).</li>
 *   <li>All-or-nothing within a single adjust() call: if any item's adjustTotal fails,
 *       the @Transactional rollback reverts all items written in this call.</li>
 *   <li>Redis addTotal is best-effort AFTER commit; failure writes an execution log
 *       and never reverts committed DB state.</li>
 * </ul>
 */
@Slf4j
@Service
public class InventoryAdjustmentDomainService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryStockRepository stockRepository;
    private final InventoryDeductGateway deductGateway;
    private final InventoryDeductRecordRepository deductRecordRepository;
    private final InventoryMetricsPort metricsPort;

    public InventoryAdjustmentDomainService(InventoryAdjustmentRepository adjustmentRepository,
                                            InventoryStockRepository stockRepository,
                                            InventoryDeductGateway deductGateway,
                                            InventoryDeductRecordRepository deductRecordRepository,
                                            InventoryMetricsPort metricsPort) {
        this.adjustmentRepository = adjustmentRepository;
        this.stockRepository = stockRepository;
        this.deductGateway = deductGateway;
        this.deductRecordRepository = deductRecordRepository;
        this.metricsPort = metricsPort;
    }

    @Transactional
    public AdjustmentResult adjust(InventoryAdjustCommand command) {
        Set<String> seen = new HashSet<>();
        for (InventoryAdjustCommand.Item item : command.getItems()) {
            if (!seen.add(item.getShopId() + ":" + item.getSkuId())) {
                metricsPort.reserveFail();
                return AdjustmentResult.fail("DUPLICATE_SKU: " + item.getSkuId());
            }
        }

        List<AdjustmentResult.AdjustedItem> newlyAdjusted = new ArrayList<>();
        for (InventoryAdjustCommand.Item item : command.getItems()) {
            if (adjustmentRepository.existsByReasonAndReferenceIdAndSku(
                    command.getReason().getCode(), command.getReferenceId(),
                    item.getShopId(), item.getSkuId())) {
                log.info("Adjustment idempotent skip: reason={}, referenceId={}, shopId={}, skuId={}",
                        command.getReason(), command.getReferenceId(), item.getShopId(), item.getSkuId());
                continue;
            }
            try {
                adjustmentRepository.saveAdjustment(item.getShopId(), item.getSkuId(),
                        item.getDelta(), command.getReason().getCode(), command.getReferenceId());
            } catch (DataIntegrityViolationException e) {
                log.info("Adjustment idempotent (unique constraint): reason={}, referenceId={}, shopId={}, skuId={}",
                        command.getReason(), command.getReferenceId(), item.getShopId(), item.getSkuId());
                continue;
            }
            stockRepository.adjustTotal(item.getShopId(), item.getSkuId(), item.getDelta());
            newlyAdjusted.add(AdjustmentResult.AdjustedItem.builder()
                    .shopId(item.getShopId()).skuId(item.getSkuId()).delta(item.getDelta()).build());
        }
        metricsPort.adjustSuccess();
        return AdjustmentResult.ok(newlyAdjusted);
    }

    /**
     * Best-effort Redis total compensation. MUST be called AFTER adjust() has committed.
     * Only newly-adjusted items are compensated (idempotent-skipped items were compensated
     * in their original call).
     */
    public void compensateRedisAfterCommit(AdjustmentResult result) {
        if (!result.isSuccess() || result.getNewlyAdjustedItems() == null) {
            return;
        }
        for (AdjustmentResult.AdjustedItem item : result.getNewlyAdjustedItems()) {
            try {
                boolean ok = deductGateway.addTotal(item.getShopId(), item.getSkuId(), item.getDelta());
                if (!ok) {
                    log.error("Redis addTotal failed after DB adjust - alert: shopId={}, skuId={}, delta={}",
                            item.getShopId(), item.getSkuId(), item.getDelta());
                    writeRedisAdjustFailedLog(item.getShopId(), item.getSkuId(), item.getDelta(),
                            "REDIS_ADDTOTAL_FAILED_AFTER_ADJUST");
                }
            } catch (Exception e) {
                log.error("Redis addTotal threw after DB adjust - alert: shopId={}, skuId={}, delta={}",
                        item.getShopId(), item.getSkuId(), item.getDelta(), e);
                writeRedisAdjustFailedLog(item.getShopId(), item.getSkuId(), item.getDelta(),
                        "REDIS_ADDTOTAL_EXCEPTION_AFTER_ADJUST: " + e.getMessage());
            }
        }
    }

    private void writeRedisAdjustFailedLog(String shopId, String skuId, long delta, String reason) {
        metricsPort.redisRollbackFailed();
        try {
            deductRecordRepository.saveRedisAdjustFailed(shopId, skuId, delta, reason);
        } catch (Exception ex) {
            log.error("Failed to persist Redis adjust failure log: shopId={}, skuId={}", shopId, skuId, ex);
        }
    }
}
