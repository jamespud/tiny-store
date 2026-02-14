package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.inventory.application.service.InventoryDeductAppService;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
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
 * InventoryDeductController Unit Test
 * 
 * Coverage:
 * - POST /api/inventory/deduct (protocol semantics)
 * - POST /api/inventory/release (protocol semantics)
 * 
 * Validates HTTP status codes, parameter validation, header requirements
 */
@WebMvcTest(
    controllers = InventoryDeductController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.spud.tinystore.inventory.infrastructure.config.WebConfig.class
    )
)
@DisplayName("InventoryDeductController Unit Tests")
class InventoryDeductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryDeductAppService deductAppService;

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/deduct")
    @DisplayName("POST /api/inventory/deduct - valid request returns 200")
    void deduct_validRequest_returns200() throws Exception {
        // Given: service returns success
        DeductResponse mockResponse = DeductResponse.ok(List.of(
                DeductResponse.OccupyPairDto.builder()
                        .shopId("SHOP-A")
                        .skuId("SKU-001")
                        .occupyId("ORDER-1_123456_5")
                        .build()
        ));
        
        when(deductAppService.deduct(anyString(), any()))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/inventory/deduct")
                .header("Idempotency-Key", "test-deduct-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORDER-1\"," +
                         "\"items\":[{\"shopId\":\"SHOP-A\",\"skuId\":\"SKU-001\",\"quantity\":5}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/deduct")
    @DisplayName("POST /api/inventory/deduct - missing Idempotency-Key returns 400")
    void deduct_missingIdempotencyKey_returns400() throws Exception {
        // When & Then: POST without idempotency header
        mockMvc.perform(post("/api/inventory/deduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORDER-1\"," +
                         "\"items\":[{\"shopId\":\"SHOP-A\",\"skuId\":\"SKU-001\",\"quantity\":5}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/release")
    @DisplayName("POST /api/inventory/release - valid request returns 200")
    void release_validRequest_returns200() throws Exception {
        // Given: service returns success
        InventoryReleaseResponse mockResponse = InventoryReleaseResponse.ok("released");
        
        when(deductAppService.release(anyString(), any()))
            .thenReturn(mockResponse);

        // When & Then: POST with valid JSON
        mockMvc.perform(post("/api/inventory/release")
                .header("Idempotency-Key", "test-release-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORDER-1\",\"reason\":\"cancel\"," +
                         "\"occupyPairs\":[{\"shopId\":\"SHOP-A\",\"skuId\":\"SKU-001\"," +
                         "\"occupyId\":\"ORDER-1_123456_5\"}]}"))
            .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Tag("ep:inventory:POST:/api/inventory/release")
    @DisplayName("POST /api/inventory/release - missing required field returns 400")
    void release_missingField_returns400() throws Exception {
        // When & Then: POST without orderId
        mockMvc.perform(post("/api/inventory/release")
                .header("Idempotency-Key", "test-release-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"cancel\"," +
                         "\"occupyPairs\":[{\"shopId\":\"SHOP-A\",\"skuId\":\"SKU-001\"," +
                         "\"occupyId\":\"ORDER-1_123456_5\"}]}"))
            .andExpect(status().isBadRequest());
    }
}
