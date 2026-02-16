package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockRestockResponse;
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
    controllers = InventoryRestockController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.inventory.infrastructure.config.WebConfig.class
    )
)
@DisplayName("InventoryRestockController Unit Tests")
class InventoryRestockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockAppService stockAppService;

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/restock")
    @DisplayName("POST /api/inventory/restock - valid request returns 200")
    void restock_validRequest_returns200() throws Exception {
        StockRestockResponse mockResponse = StockRestockResponse.ok("ok");
        when(stockAppService.restock(anyString(), any(StockRestockRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/inventory/restock")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopId\":\"SHOP_A\",\"tradeId\":\"trade-1\",\"refundId\":\"refund-1\"," +
                         "\"items\":[{\"skuId\":\"SKU_A\",\"quantity\":1}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/restock")
    @DisplayName("POST /api/inventory/restock - missing required field returns 400")
    void restock_missingField_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/restock")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-1\",\"refundId\":\"refund-1\"," +
                         "\"items\":[{\"skuId\":\"SKU_A\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
    }
}
