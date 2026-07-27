package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryAdjustmentAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryAdjustResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = InventoryAdjustmentController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.inventory.infrastructure.config.WebConfig.class)
)
@DisplayName("InventoryAdjustmentController Unit Tests")
class InventoryAdjustmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InventoryAdjustmentAppService appService;

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/adjustments")
    @DisplayName("POST /api/inventory/adjustments - valid request returns 200")
    void adjust_validRequest_returns200() throws Exception {
        when(appService.adjust(anyString(), any())).thenReturn(InventoryAdjustResponse.ok());
        mockMvc.perform(post("/api/inventory/adjustments")
                .header("Idempotency-Key", "adj-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"RESTOCK_REFUND\",\"referenceId\":\"refund-1\",\"tradeId\":\"trade-1\"," +
                         "\"items\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"delta\":5}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/adjustments")
    @DisplayName("POST /api/inventory/adjustments - missing reason returns 400")
    void adjust_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/adjustments")
                .header("Idempotency-Key", "adj-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceId\":\"refund-1\"," +
                         "\"items\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"delta\":5}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/adjustments")
    @DisplayName("POST /api/inventory/adjustments - missing Idempotency-Key returns 400")
    void adjust_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"RESTOCK_REFUND\",\"referenceId\":\"refund-1\",\"tradeId\":\"trade-1\"," +
                         "\"items\":[{\"shopId\":\"SHOP_A\",\"skuId\":\"SKU_A\",\"delta\":5}]}"))
            .andExpect(status().isBadRequest());
    }
}
