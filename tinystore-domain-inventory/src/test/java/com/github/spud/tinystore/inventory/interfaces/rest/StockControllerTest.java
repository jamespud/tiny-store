package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.StockAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockPreOccupyResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.StockReleaseResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock Controller Unit Test
 * 
 * Coverage:
 * - POST /api/inventory/stock/pre-occupy (protocol semantics)
 * - POST /api/inventory/stock/release (protocol semantics)
 * 
 * Validates HTTP status codes, parameter validation, header requirements
 */
@WebMvcTest(
    controllers = StockController.class, 
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.inventory.infrastructure.config.WebConfig.class
    )
)
@DisplayName("Stock Controller Unit Tests")
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockAppService stockAppService;

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/stock/pre-occupy")
    @DisplayName("POST /api/inventory/stock/pre-occupy - valid request returns 200")
    void preOccupy_validRequest_returns200() throws Exception {
        // Given: service returns success
        StockPreOccupyResponse mockResponse = new StockPreOccupyResponse();
        mockResponse.setSuccess(true);
        mockResponse.setPreOccupyIds(List.of("res-001"));
        
        when(stockAppService.preOccupy(anyString(), any(StockPreOccupyRequest.class)))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/inventory/stock/pre-occupy")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopId\":\"SHOP_A\",\"tradeId\":\"trade-1\"," +
                         "\"expiresAtEpochMs\":1234567890000," +
                         "\"lines\":[{\"skuId\":\"SKU_A\",\"quantity\":10}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/stock/pre-occupy")
    @DisplayName("POST /api/inventory/stock/pre-occupy - missing shopId returns 400")
    void preOccupy_missingShopId_returns400() throws Exception {
        // When & Then: POST without shopId
        mockMvc.perform(post("/api/inventory/stock/pre-occupy")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-1\"," +
                         "\"expiresAtEpochMs\":1234567890000," +
                         "\"lines\":[{\"skuId\":\"SKU_A\",\"quantity\":10}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/stock/release")
    @DisplayName("POST /api/inventory/stock/release - valid request returns 200")
    void release_validRequest_returns200() throws Exception {
        // Given: service returns success
        StockReleaseResponse mockResponse = new StockReleaseResponse();
        mockResponse.setSuccess(true);
        
        when(stockAppService.release(anyString(), any(StockReleaseRequest.class)))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/inventory/stock/release")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopId\":\"SHOP_A\",\"tradeId\":\"trade-1\"," +
                         "\"reason\":\"cancel\",\"preOccupyIds\":[\"res-001\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/stock/release")
    @DisplayName("POST /api/inventory/stock/release - missing required field returns 400")
    void release_missingField_returns400() throws Exception {
        // When & Then: POST without shopId
        mockMvc.perform(post("/api/inventory/stock/release")
                .header("Idempotency-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeId\":\"trade-1\",\"reason\":\"cancel\"}"))
            .andExpect(status().isBadRequest());
    }
}
