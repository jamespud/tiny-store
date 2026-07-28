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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReconcileJob Unit Tests")
class InventoryReconcileJobTest {

    @Mock private JpaInventoryStockRepository stockRepository;
    @Mock private InventoryReconciliationPort reconciliationPort;
    @Mock private InventoryDeductGateway deductGateway;
    @Mock private JpaInventoryReconcileLogRepository logRepository;

    @InjectMocks private InventoryReconcileJob job;

    private static final String SHOP = "shop-1";
    private static final String SKU = "sku-1";

    private void seedOneSku() {
        when(stockRepository.findAll()).thenReturn(List.of(
                new InventoryStockEntity().setShopId(SHOP).setSkuId(SKU).setTotalQuantity(100).setReservedQuantity(0)));
    }

    private ReconciliationSnapshot snap(long dbTotal, long dbConfirmed, long dbPreDeducted,
                                        Long redisTotal, Long redisDeducted) {
        return ReconciliationSnapshot.builder()
                .shopId(SHOP).skuId(SKU)
                .dbTotalQuantity(dbTotal).dbConfirmedQuantity(dbConfirmed).dbPreDeductedQuantity(dbPreDeducted)
                .redisTotal(redisTotal).redisDeducted(redisDeducted)
                .build();
    }

    @Test
    @DisplayName("totalTooHigh_triggersDecreaseTotalAndLogsRepaired")
    void totalTooHigh_triggersDecreaseTotalAndLogsRepaired() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 0, 150L, 0L)); // targetTotal=100, redisTotal=150 -> excess 50
        job.reconcile();
        verify(deductGateway).decreaseTotal(SHOP, SKU, 50L);
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("deductedTooLow_triggersIncreaseDeductedAndLogsRepaired")
    void deductedTooLow_triggersIncreaseDeductedAndLogsRepaired() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 5, 100L, 2L)); // targetDeducted=5, redisDeducted=2 -> deficit 3
        job.reconcile();
        verify(deductGateway).increaseDeducted(SHOP, SKU, 3L);
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("mixedTotalTooHighAndDeductedTooLow_repairsBoth")
    void mixedTotalTooHighAndDeductedTooLow_repairsBoth() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 5, 150L, 2L)); // excess 50, deficit 3
        job.reconcile();
        verify(deductGateway).decreaseTotal(SHOP, SKU, 50L);
        verify(deductGateway).increaseDeducted(SHOP, SKU, 3L);
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("lostSalesRisk_logsAlertNoRepair")
    void lostSalesRisk_logsAlertNoRepair() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(100, 0, 2, 80L, 9L)); // totalTooLow + deductedTooHigh -> lost-sales
        job.reconcile();
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong());
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("dbOversold_logsAlertNoRepair")
    void dbOversold_logsAlertNoRepair() {
        seedOneSku();
        when(reconciliationPort.snapshot(SHOP, SKU))
                .thenReturn(snap(-1, 0, 0, 100L, 0L));
        job.reconcile();
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong());
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
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong());
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
        verify(deductGateway, never()).decreaseTotal(any(), any(), anyLong());
        verify(deductGateway, never()).increaseDeducted(any(), any(), anyLong());
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("reconcile_continuesOnPerSkuException")
    void reconcile_continuesOnPerSkuException() {
        when(stockRepository.findAll()).thenReturn(List.of(
                new InventoryStockEntity().setShopId(SHOP).setSkuId("bad").setTotalQuantity(100).setReservedQuantity(0),
                new InventoryStockEntity().setShopId(SHOP).setSkuId("good").setTotalQuantity(100).setReservedQuantity(0)));
        when(reconciliationPort.snapshot(SHOP, "bad")).thenThrow(new RuntimeException("redis down"));
        when(reconciliationPort.snapshot(SHOP, "good")).thenReturn(snap(100, 0, 0, 100L, 0L)); // clean
        job.reconcile(); // must not propagate the "bad" exception
        verify(reconciliationPort).snapshot(SHOP, "good"); // second SKU still processed
    }
}
