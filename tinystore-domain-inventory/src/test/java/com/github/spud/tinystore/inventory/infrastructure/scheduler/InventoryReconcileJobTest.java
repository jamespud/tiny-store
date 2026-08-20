package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryReconciliationPort;
import com.github.spud.tinystore.inventory.domain.value.ReconciliationSnapshot;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReconcileLogRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReconcileJob Unit Tests")
class InventoryReconcileJobTest {

    @Mock private JpaInventoryStockRepository stockRepository;
    @Mock private InventoryReconciliationPort reconciliationPort;
    @Mock private InventoryDeductGateway deductGateway;
    @Mock private JpaInventoryReconcileLogRepository logRepository;
    @Mock private com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort metricsPort;

    @InjectMocks private InventoryReconcileJob job;

    private static final String SHOP = "shop-1";
    private static final String SKU = "sku-1";
    private static final long VER = 5L;  // 默认 snapshot version

    private void seedOneSku() {
        when(stockRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new InventoryStockEntity().setShopId(SHOP).setSkuId(SKU).setTotalQuantity(100).setReservedQuantity(0))));
    }

    private ReconciliationSnapshot snap(long dbTotal, long dbConfirmed, long dbPreDeducted,
                                        Long redisTotal, Long redisDeducted) {
        return snap(dbTotal, dbConfirmed, dbPreDeducted, redisTotal, redisDeducted, VER);
    }

    private ReconciliationSnapshot snap(long dbTotal, long dbConfirmed, long dbPreDeducted,
                                        Long redisTotal, Long redisDeducted, Long redisVersion) {
        return ReconciliationSnapshot.builder()
                .shopId(SHOP).skuId(SKU)
                .dbTotalQuantity(dbTotal).dbConfirmedQuantity(dbConfirmed).dbPreDeductedQuantity(dbPreDeducted)
                .redisTotal(redisTotal).redisDeducted(redisDeducted).redisVersion(redisVersion)
                .build();
    }

    @Test
    @DisplayName("totalTooHigh_triggersDecreaseTotalAndLogsRepaired")
    void totalTooHigh_triggersDecreaseTotalAndLogsRepaired() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, 150L, 0L)); // targetTotal=100, redisTotal=150 -> excess 50
        when(deductGateway.decreaseTotal(SHOP, SKU, 50L, VER)).thenReturn(true);
        job.reconcile();
        verify(deductGateway).decreaseTotal(SHOP, SKU, 50L, VER);
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("deductedTooLow_triggersIncreaseDeductedAndLogsRepaired")
    void deductedTooLow_triggersIncreaseDeductedAndLogsRepaired() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 5, 100L, 2L)); // targetDeducted=5, redisDeducted=2 -> deficit 3
        when(deductGateway.increaseDeducted(SHOP, SKU, 3L, VER)).thenReturn(true);
        job.reconcile();
        verify(deductGateway).increaseDeducted(SHOP, SKU, 3L, VER);
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("mixedTotalTooHighAndDeductedTooLow_repairsBoth")
    void mixedTotalTooHighAndDeductedTooLow_repairsBoth() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 5, 150L, 2L)); // excess 50, deficit 3
        when(deductGateway.decreaseTotal(SHOP, SKU, 50L, VER)).thenReturn(true);
        when(deductGateway.increaseDeducted(SHOP, SKU, 3L, VER)).thenReturn(true);
        job.reconcile();
        verify(deductGateway).decreaseTotal(SHOP, SKU, 50L, VER);
        verify(deductGateway).increaseDeducted(SHOP, SKU, 3L, VER);
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("lostSalesRisk_logsAlertNoRepair")
    void lostSalesRisk_logsAlertNoRepair() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 2, 80L, 9L)); // totalTooLow + deductedTooHigh -> lost-sales
        job.reconcile();
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("dbOversold_logsAlertNoRepair")
    void dbOversold_logsAlertNoRepair() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(-1, 0, 0, 100L, 0L));
        job.reconcile();
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("negativeAvailable_logsAlertNoRepair_evenIfTotalTooHigh")
    void negativeAvailable_logsAlertNoRepair_evenIfTotalTooHigh() {
        seedOneSku();
        // redisTotal=110 (>target 100 -> totalTooHigh) but redisDeducted=120 -> available -10 -> negativeAvailable
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, 110L, 120L));
        job.reconcile();
        // negativeAvailable is checked before oversell repair -> no repair
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("clean_skipsNoLog")
    void clean_skipsNoLog() {
        seedOneSku();
        // confirmed=5, targetTotal=105, targetDeducted=5; Redis matches -> in sync
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 5, 0, 105L, 5L));
        job.reconcile();
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong(), anyLong());
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("reconcile_continuesOnPerSkuException")
    void reconcile_continuesOnPerSkuException() {
        when(stockRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new InventoryStockEntity().setShopId(SHOP).setSkuId("bad").setTotalQuantity(100).setReservedQuantity(0),
                new InventoryStockEntity().setShopId(SHOP).setSkuId("good").setTotalQuantity(100).setReservedQuantity(0))));
        when(reconciliationPort.snapshot(SHOP, "bad")).thenThrow(new RuntimeException("redis down"));
        when(reconciliationPort.snapshot(SHOP, "good")).thenReturn(snap(100, 0, 0, 100L, 0L)); // clean
        job.reconcile(); // must not propagate the "bad" exception
        verify(reconciliationPort).snapshot(SHOP, "good"); // second SKU still processed
    }

    @Test
    @DisplayName("reconcile_iteratesAllPages")
    void reconcile_iteratesAllPages() {
        InventoryStockEntity skuA = new InventoryStockEntity().setShopId(SHOP).setSkuId("sku-A")
                .setTotalQuantity(100).setReservedQuantity(0);
        InventoryStockEntity skuB = new InventoryStockEntity().setShopId(SHOP).setSkuId("sku-B")
                .setTotalQuantity(100).setReservedQuantity(0);
        InventoryStockEntity skuC = new InventoryStockEntity().setShopId(SHOP).setSkuId("sku-C")
                .setTotalQuantity(100).setReservedQuantity(0);
        PageRequest firstReq = PageRequest.of(0, 2);
        PageRequest secondReq = PageRequest.of(1, 2);
        // Job pages with its own pageSize; mock returns page1 then page2 in sequence.
        when(stockRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(skuA, skuB), firstReq, 3),
                            new PageImpl<>(List.of(skuC), secondReq, 3));
        when(reconciliationPort.snapshot(eq(SHOP), anyString()))
                .thenReturn(snap(100, 0, 0, 100L, 0L)); // all clean
        job.reconcile();
        verify(reconciliationPort).snapshot(SHOP, "sku-A");
        verify(reconciliationPort).snapshot(SHOP, "sku-B");
        verify(reconciliationPort).snapshot(SHOP, "sku-C");
        verify(stockRepository, times(2)).findAll(any(Pageable.class));
    }

    // ===== Task 3: version-CAS skip / partial / version-null =====

    @Test
    @DisplayName("casSkip_totalTooHigh_logsSkippedNotMetric")
    void casSkip_totalTooHigh_logsSkippedNotMetric() {
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, 150L, 0L)); // excess 50
        when(deductGateway.decreaseTotal(SHOP, SKU, 50L, VER)).thenReturn(false);  // CAS 跳过

        boolean acted = job.reconcileOne(SHOP, SKU);

        assertThat(acted).isTrue();  // 写了 CAS_SKIPPED 日志
        verify(metricsPort, never()).reconcileRepaired();  // 不计修复 metric
        verify(logRepository).save(argThat(e -> "CAS_SKIPPED".equals(e.getAction())));
    }

    @Test
    @DisplayName("partialSuccess_totalApplied_deductedSkipped_logsRepairedWithTotalOnly")
    void partialSuccess_totalApplied_deductedSkipped_logsRepairedWithTotalOnly() {
        // total 过高 + deducted 过低
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 10L, 120L, 0L));  // excess 20, deficit 10
        when(deductGateway.decreaseTotal(SHOP, SKU, 20L, VER)).thenReturn(true);
        when(deductGateway.increaseDeducted(SHOP, SKU, 10L, VER)).thenReturn(false);  // CAS 跳过

        boolean acted = job.reconcileOne(SHOP, SKU);

        assertThat(acted).isTrue();
        verify(metricsPort).reconcileRepaired();  // 有修复 -> 计 metric
        verify(logRepository).save(argThat(e -> "REPAIRED_OVERSELL".equals(e.getAction())
                && "TOTAL".equals(e.getRepairedFields())));
    }

    @Test
    @DisplayName("versionMissing_initializesStateForCasRepair")
    void versionMissing_initializesStateForCasRepair() {
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, 150L, 0L, null));  // version key 缺失
        when(deductGateway.initState(SHOP, SKU, 100L, 0L)).thenReturn(true);

        boolean acted = job.reconcileOne(SHOP, SKU);

        // 缺失 version -> 权威初始化补建（Fix 1），不再"跳过且不写日志"
        assertThat(acted).isTrue();
        verify(deductGateway).initState(SHOP, SKU, 100L, 0L);
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong(), anyLong());
        verify(logRepository).save(argThat(e -> "INITIALIZED_KEYS".equals(e.getAction())));
    }

    // ===== Fix 1: missing V2 keys (Redis flush recovery) -> authoritative init =====

    @Test
    @DisplayName("missingKeys_triggersInitStateAndLogsInitialized")
    void missingKeys_triggersInitStateAndLogsInitialized() {
        seedOneSku();
        // confirmed=5, keys all absent (Redis flush): targetTotal=100, targetDeducted=5
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(95, 5, 0, null, null, null));
        when(deductGateway.initState(SHOP, SKU, 100L, 5L)).thenReturn(true);

        job.reconcile();

        verify(deductGateway).initState(SHOP, SKU, 100L, 5L);
        verify(logRepository).save(argThat(e -> "INITIALIZED_KEYS".equals(e.getAction())));
        verify(metricsPort).reconcileRepaired();
    }

    @Test
    @DisplayName("missingKeys_concurrentInitWon_noRepairMetric")
    void missingKeys_concurrentInitWon_noRepairMetric() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, null, null, null));
        when(deductGateway.initState(SHOP, SKU, 100L, 0L)).thenReturn(false); // 并发已建

        job.reconcile();

        verify(deductGateway).initState(SHOP, SKU, 100L, 0L);
        verify(metricsPort, never()).reconcileRepaired();
    }
}
