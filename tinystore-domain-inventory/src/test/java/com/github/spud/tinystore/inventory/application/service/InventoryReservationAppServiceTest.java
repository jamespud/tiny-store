package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.domain.enums.InventoryReservationStatus;
import com.github.spud.tinystore.inventory.domain.service.InventoryReservationDomainService;
import com.github.spud.tinystore.inventory.domain.value.ReservationResult;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReservationAppService Unit Tests")
class InventoryReservationAppServiceTest {

    @Mock
    private InventoryReservationDomainService domainService;

    private InventoryReservationAppService appService;

    @BeforeEach
    void setUp() {
        appService = new InventoryReservationAppService(domainService);
        ReflectionTestUtils.setField(appService, "defaultExpiryMinutes", 30);
    }

    @Test
    @DisplayName("reserve_stockLack_shouldExposeLackSkuIdsInDeductResponse")
    void reserve_stockLack_shouldExposeLackSkuIdsInDeductResponse() {
        DeductRequest request = deductRequest("trade-001", "order-001", "shop-001", "sku-001", 1);

        when(domainService.reserve(any())).thenReturn(
                ReservationResult.fail(List.of("sku-001"), "STOCK_LACK: sku-001")
        );

        DeductResponse response = appService.reserve("idem-001", request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("STOCK_LACK: sku-001");
        assertThat(response.getLackSkuIds()).containsExactly("sku-001");
        assertThat(response.getOccupyPairs()).isEmpty();
    }

    @Test
    @DisplayName("reserve_success_shouldMapReservationRefsToOccupyPairs")
    void reserve_success_shouldMapReservationRefsToOccupyPairs() {
        DeductRequest request = deductRequest("trade-001", "order-001", "shop-001", "sku-001", 1);

        when(domainService.reserve(any())).thenReturn(
                ReservationResult.ok(List.of(
                        com.github.spud.tinystore.inventory.domain.value.ReservationRef.builder()
                                .shopId("shop-001")
                                .skuId("sku-001")
                                .reservationId("res-001")
                                .build()
                ), InventoryReservationStatus.PRE_DEDUCTED)
        );

        DeductResponse response = appService.reserve("idem-001", request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOccupyPairs()).hasSize(1);
        assertThat(response.getOccupyPairs().get(0).getOccupyId()).isEqualTo("res-001");
        assertThat(response.getLackSkuIds()).isEmpty();
    }

        @Test
        @DisplayName("reserve_successWithoutRefs_shouldReturnFailure")
        void reserve_successWithoutRefs_shouldReturnFailure() {
                DeductRequest request = deductRequest("trade-002", "order-002", "shop-002", "sku-002", 1);

                when(domainService.reserve(any())).thenReturn(
                                ReservationResult.ok(List.of(), InventoryReservationStatus.PRE_DEDUCTED)
                );

                DeductResponse response = appService.reserve("idem-002", request);

                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getMessage()).isEqualTo("RESERVATION_REFS_MISSING");
                assertThat(response.getOccupyPairs()).isEmpty();
        }

        private DeductRequest deductRequest(String tradeId, String orderId, String shopId, String skuId, int quantity) {
                DeductRequest.Item item = new DeductRequest.Item();
                item.setShopId(shopId);
                item.setSkuId(skuId);
                item.setQuantity(quantity);

                DeductRequest request = new DeductRequest();
                request.setTradeId(tradeId);
                request.setOrderId(orderId);
                request.setItems(List.of(item));
                return request;
        }
}