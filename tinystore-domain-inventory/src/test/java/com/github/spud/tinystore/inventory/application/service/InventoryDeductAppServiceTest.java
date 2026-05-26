package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.domain.service.InventoryDeductDomainService;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryDeductAppService Unit Tests")
class InventoryDeductAppServiceTest {

    @Mock
    private InventoryDeductDomainService legacyDomainService;

    @Mock
    private InventoryReservationAppService canonicalService;

    private InventoryDeductAppService appService;

    @BeforeEach
    void setUp() {
        appService = new InventoryDeductAppService(legacyDomainService, canonicalService);
        ReflectionTestUtils.setField(appService, "canonicalEnabled", true);
    }

    @Test
    @DisplayName("deduct_canonicalEnabled_missingTradeId_shouldFallbackToOrderId")
    void deduct_canonicalEnabled_missingTradeId_shouldFallbackToOrderId() {
        DeductRequest request = new DeductRequest();
        request.setOrderId("order-001");

        DeductRequest.Item item = new DeductRequest.Item();
        item.setShopId("shop-001");
        item.setSkuId("sku-001");
        item.setQuantity(1);
        request.setItems(List.of(item));

        DeductResponse expected = DeductResponse.ok(List.of());
        when(canonicalService.reserve(anyString(), any())).thenReturn(expected);

        DeductResponse actual = appService.deduct("idem-001", request);

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<DeductRequest> requestCaptor = ArgumentCaptor.forClass(DeductRequest.class);
        verify(canonicalService).reserve(anyString(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTradeId()).isEqualTo("order-001");
    }
}