package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort;
import com.github.spud.tinystore.inventory.domain.port.InventoryReconciliationPort;
import com.github.spud.tinystore.inventory.domain.value.ReconciliationSnapshot;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReconcileLogEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReconcileLogRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodic inventory reconciliation (Priority 2).
 * <p>
 * Scans every (shopId, skuId); uses the read-only InventoryReconciliationPort to detect
 * Redis/DB desync. Auto-repairs ONLY the oversell-risk direction using additive
 * DECRBY/INCRBY (never SET, never the lost-sales direction). Lost-sales, dbOversold and
 * negativeAvailable are alert-only (repair would risk creating oversell).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "inventory.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReconcileJob {

    private final JpaInventoryStockRepository stockRepository;
    private final InventoryReconciliationPort reconciliationPort;
    private final InventoryDeductGateway deductGateway;
    private final JpaInventoryReconcileLogRepository logRepository;
    private final InventoryMetricsPort metricsPort;

    public InventoryReconcileJob(JpaInventoryStockRepository stockRepository,
                                 InventoryReconciliationPort reconciliationPort,
                                 InventoryDeductGateway deductGateway,
                                 JpaInventoryReconcileLogRepository logRepository,
                                 InventoryMetricsPort metricsPort) {
        this.stockRepository = stockRepository;
        this.reconciliationPort = reconciliationPort;
        this.deductGateway = deductGateway;
        this.logRepository = logRepository;
        this.metricsPort = metricsPort;
    }

    @Scheduled(fixedDelayString = "${inventory.reconciliation.fixed-delay:PT10M}")
    public void reconcile() {
        // Paged scan: bound memory for large catalogs (no full-table in-memory load).
        int actions = 0;
        int page = 0;
        int pageSize = 500;
        while (true) {
            Page<InventoryStockEntity> stocks = stockRepository.findAll(PageRequest.of(page, pageSize));
            for (InventoryStockEntity stock : stocks) {
                try {
                    if (reconcileOne(stock.getShopId(), stock.getSkuId())) {
                        actions++;
                    }
                } catch (Exception e) {
                    log.error("Reconcile failed for shopId={}, skuId={}", stock.getShopId(), stock.getSkuId(), e);
                }
            }
            if (stocks.isLast()) {
                break;
            }
            page++;
        }
        if (actions > 0) {
            log.info("Reconcile pass: {} actions logged", actions);
        }
    }

    /** @return true if a log row was written (an action was taken) */
    boolean reconcileOne(String shopId, String skuId) {
        ReconciliationSnapshot snap = reconciliationPort.snapshot(shopId, skuId);

        if (snap.isDbOversold()) {
            metricsPort.reconcileAlert();
            saveLog(shopId, skuId, snap, "ALERT_DB_OVERSOLD", null);
            return true;
        }

        // Missing V2 keys (e.g., Redis flush/restart): (re)initialize from authoritative DB targets.
        // SETNX semantics — never clobbers an existing key; a concurrent business write wins (no-op).
        if (snap.getRedisTotal() == null || snap.getRedisDeducted() == null || snap.getRedisVersion() == null) {
            boolean applied = deductGateway.initState(shopId, skuId, snap.getTargetTotal(), snap.getTargetDeducted());
            if (applied) {
                metricsPort.reconcileRepaired();
                saveLog(shopId, skuId, snap, "INITIALIZED_KEYS", "TOTAL,DEDUCTED,VERSION");
                return true;
            }
            // 并发其它实例/业务已补建：本轮无动作，下轮重新评估
            saveLog(shopId, skuId, snap, "CAS_SKIPPED", "INIT");
            return true;
        }

        if (snap.isNegativeAvailable()) {
            // Inconsistent state (deducted > total); conservative repair would worsen over-reject. Alert only.
            metricsPort.reconcileAlert();
            saveLog(shopId, skuId, snap, "ALERT_NEGATIVE_AVAILABLE", null);
            return true;
        }

        List<String> repaired = new ArrayList<>();
        // 双字段原子修复：仅超卖方向（total 过高 / deducted 过低），一次 CAS 同时应用，无部分修复
        long totalDelta = 0;
        long deductedDelta = 0;
        if (snap.isTotalTooHigh()) {
            totalDelta = snap.getTargetTotal() - snap.getRedisTotal(); // <0：下调 total
            repaired.add("TOTAL");
        }
        if (snap.isDeductedTooLow()) {
            deductedDelta = snap.getTargetDeducted() - snap.getRedisDeducted(); // >0：上调 deducted
            repaired.add("DEDUCTED");
        }
        if (!repaired.isEmpty()) {
            boolean applied = deductGateway.repairOversell(shopId, skuId, totalDelta, deductedDelta, snap.getRedisVersion());
            if (applied) {
                metricsPort.reconcileRepaired();
                saveLog(shopId, skuId, snap, "REPAIRED_OVERSELL", String.join(",", repaired));
                return true;
            }
            // CAS 跳过（version 已被并发业务 bump）：不计修复 metric，下轮重新快照评估
            saveLog(shopId, skuId, snap, "CAS_SKIPPED", String.join(",", repaired));
            return true;
        }

        if (snap.isLostSalesRisk()) {
            metricsPort.reconcileAlert();
            saveLog(shopId, skuId, snap, "ALERT_LOST_SALES", null);
            return true;
        }
        return false; // clean: no log
    }

    private void saveLog(String shopId, String skuId, ReconciliationSnapshot snap,
                         String action, String repairedFields) {
        try {
            logRepository.save(new InventoryReconcileLogEntity()
                    .setShopId(shopId).setSkuId(skuId)
                    .setDbTotal(snap.getDbTotalQuantity())
                    .setDbConfirmed(snap.getDbConfirmedQuantity())
                    .setDbPreDeducted(snap.getDbPreDeductedQuantity())
                    .setRedisTotalBefore(snap.getRedisTotal())
                    .setRedisDeductedBefore(snap.getRedisDeducted())
                    .setTargetTotal(snap.getTargetTotal())
                    .setTargetDeducted(snap.getTargetDeducted())
                    .setAction(action)
                    .setRepairedFields(repairedFields));
        } catch (Exception e) {
            log.error("Failed to persist reconcile log: shopId={}, skuId={}, action={}", shopId, skuId, action, e);
        }
    }
}
