package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryDeductCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductGateway;
import com.github.spud.tinystore.inventory.domain.port.InventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.domain.value.DeductResult;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Domain Service Unit Test for InventoryDeductDomainService
 * 
 * Coverage:
 * - Deduct success path with Redis + DB record
 * - Deduct failure compensation when SKU lacks stock
 * - Deduct failure compensation when DB record fails
 * - Release success path
 * - Release idempotency boundary (prevent short-circuit by deduct cache)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryDeductDomainService Unit Tests")
class InventoryDeductDomainServiceTest {

    @Mock
    private InventoryDeductGateway deductGateway;

    @Mock
    private InventoryDeductRecordRepository recordRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @InjectMocks
    private InventoryDeductDomainService domainService;

    @Test
    @DisplayName("deduct_success_shouldReturnOccupyPairs_andSaveRecord_andCacheIdem")
    void deduct_success_shouldReturnOccupyPairsAndSaveRecordAndCacheIdem() {
        // Given: no cached idempotency
        when(idempotencyRepository.getDeductResult(anyString())).thenReturn(Optional.empty());
        
        // Given
        String orderId = "ORDER-001";
        String idemKey = "idem-deduct-1";
        InventoryDeductCommand command = InventoryDeductCommand.builder()
                .orderId(orderId)
                .idempotencyKey(idemKey)
                .items(List.of(
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP1")
                                .skuId("SKU1")
                                .quantity(5)
                                .build(),
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP1")
                                .skuId("SKU2")
                                .quantity(3)
                                .build()
                ))
                .build();

        when(deductGateway.preDeduct("SHOP1", "SKU1", 5, orderId))
                .thenReturn(Optional.of("ORDER-001_1234567890_5"));
        when(deductGateway.preDeduct("SHOP1", "SKU2", 3, orderId))
                .thenReturn(Optional.of("ORDER-001_1234567891_3"));

        // When
        DeductResult result = domainService.deduct(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getOccupyPairs()).hasSize(2);
        assertThat(result.getOccupyPairs().get(0).getShopId()).isEqualTo("SHOP1");
        assertThat(result.getOccupyPairs().get(0).getSkuId()).isEqualTo("SKU1");
        assertThat(result.getOccupyPairs().get(0).getOccupyId()).isEqualTo("ORDER-001_1234567890_5");

        // Verify DB record saved
        verify(recordRepository).saveDeducted(eq(orderId), eq(idemKey), anyList(), anyList());

        // Verify idempotency cached
        verify(idempotencyRepository).saveDeductResult(eq(idemKey), any(DeductResult.class));
    }

    @Test
    @DisplayName("deduct_whenSecondSkuLack_shouldRollbackFirst_andReturnFail")
    void deduct_whenSecondSkuLack_shouldRollbackFirstAndReturnFail() {
        // Given
        when(idempotencyRepository.getDeductResult(anyString())).thenReturn(Optional.empty());

        String orderId = "ORDER-002";
        InventoryDeductCommand command = InventoryDeductCommand.builder()
                .orderId(orderId)
                .idempotencyKey("idem-deduct-2")
                .items(List.of(
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP2")
                                .skuId("SKU-A")
                                .quantity(10)
                                .build(),
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP2")
                                .skuId("SKU-B")
                                .quantity(20)
                                .build()
                ))
                .build();

        // First SKU succeeds, second fails
        when(deductGateway.preDeduct("SHOP2", "SKU-A", 10, orderId))
                .thenReturn(Optional.of("ORDER-002_1000_10"));
        when(deductGateway.preDeduct("SHOP2", "SKU-B", 20, orderId))
                .thenReturn(Optional.empty()); // Stock lack

        // When
        DeductResult result = domainService.deduct(command);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("STOCK_LACK");
        assertThat(result.getLackSkuIds()).contains("SKU-B");

        // Verify rollback called for first SKU
        verify(deductGateway).rollback("SHOP2", "SKU-A", "ORDER-002_1000_10");

        // Verify DB record NOT saved
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("deduct_whenDbRecordFails_shouldRollbackAll_andReturnDbRecordFailed")
    void deduct_whenDbRecordFails_shouldRollbackAllAndReturnDbRecordFailed() {
        // Given
        when(idempotencyRepository.getDeductResult(anyString())).thenReturn(Optional.empty());

        String orderId = "ORDER-003";
        String idemKey = "idem-deduct-3";
        InventoryDeductCommand command = InventoryDeductCommand.builder()
                .orderId(orderId)
                .idempotencyKey(idemKey)
                .items(List.of(
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP3")
                                .skuId("SKU-X")
                                .quantity(2)
                                .build(),
                        InventoryDeductCommand.Item.builder()
                                .shopId("SHOP3")
                                .skuId("SKU-Y")
                                .quantity(4)
                                .build()
                ))
                .build();

        when(deductGateway.preDeduct("SHOP3", "SKU-X", 2, orderId))
                .thenReturn(Optional.of("ORDER-003_2000_2"));
        when(deductGateway.preDeduct("SHOP3", "SKU-Y", 4, orderId))
                .thenReturn(Optional.of("ORDER-003_2001_4"));

        // DB saveDeducted throws exception
        doThrow(new RuntimeException("DB connection failed"))
                .when(recordRepository).saveDeducted(anyString(), anyString(), anyList(), anyList());

        // When
        DeductResult result = domainService.deduct(command);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("DB_RECORD_FAILED");

        // Verify rollback called for ALL successful preDeducts
        verify(deductGateway).rollback("SHOP3", "SKU-X", "ORDER-003_2000_2");
        verify(deductGateway).rollback("SHOP3", "SKU-Y", "ORDER-003_2001_4");

        // Verify idempotency NOT cached (since failure)
        verify(idempotencyRepository, never()).saveDeductResult(anyString(), any());
    }

    @Test
    @DisplayName("release_shouldRollbackAll_andMarkReleased_andBestEffortDbMark")
    void release_shouldRollbackAllAndMarkReleasedAndBestEffortDbMark() {
        // Given
        when(idempotencyRepository.isReleased(anyString())).thenReturn(false);

        String orderId = "ORDER-004";
        String idemKey = "idem-release-1";
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .orderId(orderId)
                .idempotencyKey(idemKey)
                .reason("order-cancelled")
                .occupyPairs(List.of(
                        OccupyPair.builder()
                                .shopId("SHOP4")
                                .skuId("SKU-P")
                                .occupyId("ORDER-004_3000_5")
                                .build(),
                        OccupyPair.builder()
                                .shopId("SHOP4")
                                .skuId("SKU-Q")
                                .occupyId("ORDER-004_3001_7")
                                .build()
                ))
                .build();

        when(deductGateway.rollback(anyString(), anyString(), anyString())).thenReturn(true);

        // When
        DeductResult result = domainService.release(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");

        // Verify rollback called for each pair
        verify(deductGateway).rollback("SHOP4", "SKU-P", "ORDER-004_3000_5");
        verify(deductGateway).rollback("SHOP4", "SKU-Q", "ORDER-004_3001_7");

        // Verify DB markReleased called (best-effort)
        ArgumentCaptor<List<OccupyPair>> pairsCaptor = ArgumentCaptor.forClass(List.class);
        verify(recordRepository).markReleased(eq(orderId), pairsCaptor.capture(), eq("order-cancelled"));
        assertThat(pairsCaptor.getValue()).hasSize(2);

        // Verify idempotency marked
        verify(idempotencyRepository).markReleased(idemKey);
    }

    @Test
    @DisplayName("release_shouldNotShortCircuit_whenOnlyDeductIdemExists")
    void release_shouldNotShortCircuit_whenOnlyDeductIdemExists() {
        // Given: Deduct idempotency exists, but release NOT yet marked
        String orderId = "ORDER-005";
        String sharedIdemKey = "shared-idem-key"; // Incorrectly reused key

        // isReleased should return false (deduct idem exists but release not marked)
        when(idempotencyRepository.isReleased(sharedIdemKey)).thenReturn(false);

        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .orderId(orderId)
                .idempotencyKey(sharedIdemKey)
                .reason("test-reused-key")
                .occupyPairs(List.of(
                        OccupyPair.builder()
                                .shopId("SHOP5")
                                .skuId("SKU-R")
                                .occupyId("ORDER-005_4000_3")
                                .build()
                ))
                .build();

        when(deductGateway.rollback(anyString(), anyString(), anyString())).thenReturn(true);

        // When
        DeductResult result = domainService.release(command);

        // Then: Should still execute rollback (not short-circuited by deduct idem)
        assertThat(result.isSuccess()).isTrue();
        verify(deductGateway).rollback("SHOP5", "SKU-R", "ORDER-005_4000_3");
        verify(idempotencyRepository).markReleased(sharedIdemKey);
    }
}
