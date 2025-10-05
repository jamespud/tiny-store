package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.PricingService;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.interfaces.dto.PricingResultDTO;
import com.github.spud.tinystore.product.interfaces.mapper.PricingDTOMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PricingControllerTest - Unit tests for PricingController REST endpoints
 * 
 * Tests dynamic pricing calculation endpoint.
 */
@WebMvcTest(PricingController.class)
@DisplayName("PricingController Unit Tests")
class PricingControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private PricingService pricingService;
    
    @MockitoBean
    private PricingDTOMapper pricingDTOMapper;
    
    @Test
    @DisplayName("POST /api/products/calculate-price - Calculate price successfully returns 200 OK")
    void calculatePrice_Success_Returns200() throws Exception {
        // Given
        PricingResult mockResult = mock(PricingResult.class);
        PricingResultDTO responseDTO = mock(PricingResultDTO.class);
        
        when(pricingService.calculatePrice(any())).thenReturn(mockResult);
        when(pricingDTOMapper.toResponseDTO(mockResult)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(post("/api/products/calculate-price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":\"sku-123\",\"userId\":\"user-456\"}"))
            .andExpect(status().isOk());
        
        verify(pricingService).calculatePrice(any());
    }
    
    @Test
    @DisplayName("POST /api/products/calculate-price - Invalid context returns 400 Bad Request")
    void calculatePrice_InvalidContext_Returns400() throws Exception {
        // When & Then - Missing required field skuId
        mockMvc.perform(post("/api/products/calculate-price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("POST /api/products/calculate-price - SKU not found returns 404")
    void calculatePrice_SkuNotFound_Returns404() throws Exception {
        // Given
        when(pricingService.calculatePrice(any()))
            .thenThrow(new java.util.NoSuchElementException("SKU not found"));
        
        // When & Then
        mockMvc.perform(post("/api/products/calculate-price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":\"non-existent\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
