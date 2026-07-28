package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryAdjustCommand;
import com.github.spud.tinystore.inventory.domain.enums.AdjustmentReason;
import com.github.spud.tinystore.inventory.domain.port.InventoryAdjustmentRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryStockRepository;
import com.github.spud.tinystore.inventory.domain.value.AdjustmentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAdjustmentDomainService Unit Tests")
class InventoryAdjustmentDomainServiceTest {

    @Mock private InventoryAdjustmentRepository adjustmentRepository;
    @Mock private InventoryStockRepository stockRepository;
    @Mock private InventoryDeductGateway deductGateway;
    @Mock private InventoryDeductRecordRepository deductRecordRepository;
    @Mock private com.github.spud.tinystore.inventory.domain.port.InventoryMetricsPort metricsPort;

    @InjectMocks private InventoryAdjustmentDomainService domainService;

    private static final String SHOP = "shop-1";
    private static final String SKU = "sku-1";
    private static final String REFUND_ID = "refund-1";

    private InventoryAdjustCommand refundCmd(String shopId, String skuId, long delta) {
        return InventoryAdjustCommand.builder()
                .idempotencyKey("idem-1")
                .reason(AdjustmentReason.RESTOCK_REFUND)
                .referenceId(REFUND_ID)
                .tradeId("trade-1")
                .items(List.of(InventoryAdjustCommand.Item.builder()
                        .shopId(shopId).skuId(skuId).delta(delta).build()))
                .build();
    }

    @Test
    @DisplayName("adjust_singleSku_positiveDelta_shouldWriteLogAndAdjustStock")
    void adjust_singleSku_positiveDelta_shouldWriteLogAndAdjustStock() {
        InventoryAdjustCommand cmd = refundCmd(SHOP, SKU, 5);
        when(adjustmentRepository.existsByReasonAndReferenceIdAndSku(any(), any(), any(), any()))
                .thenReturn(false);

        AdjustmentResult result = domainService.adjust(cmd);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewlyAdjustedItems()).hasSize(1);
        verify(adjustmentRepository).saveAdjustment(SHOP, SKU, 5, "RESTOCK_REFUND", REFUND_ID);
        verify(stockRepository).adjustTotal(SHOP, SKU, 5);
    }

    @Test
    @DisplayName("adjust_multiSku_shouldWritePerSkuLogsAndAdjustEach")
    void adjust_multiSku_shouldWritePerSkuLogsAndAdjustEach() {
        InventoryAdjustCommand cmd = InventoryAdjustCommand.builder()
                .idempotencyKey("idem-1")
                .reason(AdjustmentReason.RESTOCK_REFUND)
                .referenceId(REFUND_ID)
                .tradeId("trade-1")
                .items(List.of(
                        InventoryAdjustCommand.Item.builder().shopId(SHOP).skuId("sku-A").delta(2).build(),
                        InventoryAdjustCommand.Item.builder().shopId(SHOP).skuId("sku-B").delta(3).build()))
                .build();
        when(adjustmentRepository.existsByReasonAndReferenceIdAndSku(any(), any(), any(), any()))
                .thenReturn(false);

        AdjustmentResult result = domainService.adjust(cmd);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewlyAdjustedItems()).hasSize(2);
        verify(adjustmentRepository).saveAdjustment(SHOP, "sku-A", 2, "RESTOCK_REFUND", REFUND_ID);
        verify(adjustmentRepository).saveAdjustment(SHOP, "sku-B", 3, "RESTOCK_REFUND", REFUND_ID);
        verify(stockRepository).adjustTotal(SHOP, "sku-A", 2);
        verify(stockRepository).adjustTotal(SHOP, "sku-B", 3);
    }

    @Test
    @DisplayName("adjust_idempotentHit_shouldSkipAndNotAdjustStock")
    void adjust_idempotentHit_shouldSkipAndNotAdjustStock() {
        InventoryAdjustCommand cmd = refundCmd(SHOP, SKU, 5);
        when(adjustmentRepository.existsByReasonAndReferenceIdAndSku(any(), any(), any(), any()))
                .thenReturn(true);

        AdjustmentResult result = domainService.adjust(cmd);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewlyAdjustedItems()).isEmpty();
        verify(adjustmentRepository, never()).saveAdjustment(any(), any(), anyLong(), any(), any());
        verify(stockRepository, never()).adjustTotal(any(), any(), anyLong());
    }

    @Test
    @DisplayName("adjust_concurrentUniqueConstraint_shouldTreatAsIdempotent")
    void adjust_concurrentUniqueConstraint_shouldTreatAsIdempotent() {
        InventoryAdjustCommand cmd = refundCmd(SHOP, SKU, 5);
        when(adjustmentRepository.existsByReasonAndReferenceIdAndSku(any(), any(), any(), any()))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("dup"))
                .when(adjustmentRepository).saveAdjustment(any(), any(), anyLong(), any(), any());

        AdjustmentResult result = domainService.adjust(cmd);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewlyAdjustedItems()).isEmpty();
        verify(stockRepository, never()).adjustTotal(any(), any(), anyLong());
    }

    @Test
    @DisplayName("adjust_negativeDeltaInsufficientStock_shouldThrowAndRollback")
    void adjust_negativeDeltaInsufficientStock_shouldThrowAndRollback() {
        InventoryAdjustCommand cmd = refundCmd(SHOP, SKU, -100);
        when(adjustmentRepository.existsByReasonAndReferenceIdAndSku(any(), any(), any(), any()))
                .thenReturn(false);
        doThrow(new IllegalStateException("Insufficient stock"))
                .when(stockRepository).adjustTotal(SHOP, SKU, -100);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> domainService.adjust(cmd))
                .isInstanceOf(IllegalStateException.class);
        // saveAdjustment ran but adjustTotal threw -> @Transactional rolls back the whole call
        verify(adjustmentRepository).saveAdjustment(SHOP, SKU, -100, "RESTOCK_REFUND", REFUND_ID);
    }

    @Test
    @DisplayName("adjust_duplicateSkuInCommand_shouldFail")
    void adjust_duplicateSkuInCommand_shouldFail() {
        InventoryAdjustCommand cmd = InventoryAdjustCommand.builder()
                .idempotencyKey("idem-1")
                .reason(AdjustmentReason.RESTOCK_REFUND)
                .referenceId(REFUND_ID)
                .tradeId("trade-1")
                .items(List.of(
                        InventoryAdjustCommand.Item.builder().shopId(SHOP).skuId(SKU).delta(1).build(),
                        InventoryAdjustCommand.Item.builder().shopId(SHOP).skuId(SKU).delta(2).build()))
                .build();

        AdjustmentResult result = domainService.adjust(cmd);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("DUPLICATE_SKU");
        verify(adjustmentRepository, never()).saveAdjustment(any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("compensateRedisAfterCommit_shouldCallAddTotalForNewlyAdjustedOnly")
    void compensateRedisAfterCommit_shouldCallAddTotalForNewlyAdjustedOnly() {
        AdjustmentResult result = AdjustmentResult.ok(List.of(
                AdjustmentResult.AdjustedItem.builder().shopId(SHOP).skuId(SKU).delta(5).build()));

        domainService.compensateRedisAfterCommit(result);

        verify(deductGateway).addTotal(SHOP, SKU, 5);
    }

    @Test
    @DisplayName("compensateRedisAfterCommit_redisFailure_shouldWriteExecutionLogNotThrow")
    void compensateRedisAfterCommit_redisFailure_shouldWriteExecutionLogNotThrow() {
        AdjustmentResult result = AdjustmentResult.ok(List.of(
                AdjustmentResult.AdjustedItem.builder().shopId(SHOP).skuId(SKU).delta(5).build()));
        when(deductGateway.addTotal(SHOP, SKU, 5)).thenReturn(false);

        domainService.compensateRedisAfterCommit(result);

        verify(deductRecordRepository).saveRedisAdjustFailed(eq(SHOP), eq(SKU), eq(5L), contains("REDIS_ADDTOTAL_FAILED"));
    }

    @Test
    @DisplayName("compensateRedisAfterCommit_failedResult_shouldBeNoop")
    void compensateRedisAfterCommit_failedResult_shouldBeNoop() {
        domainService.compensateRedisAfterCommit(AdjustmentResult.fail("bad"));
        verifyNoInteractions(deductGateway);
    }
}
