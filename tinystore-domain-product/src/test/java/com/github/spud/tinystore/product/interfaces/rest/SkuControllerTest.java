package com.github.spud.tinystore.product.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.spud.tinystore.product.application.service.SkuService;
import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.interfaces.dto.SkuCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuResponseDTO;
import com.github.spud.tinystore.product.interfaces.mapper.SkuDTOMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SkuControllerTest - Unit tests for SkuController REST endpoints
 * 
 * Tests HTTP semantics, status codes, and service integration for SKU operations.
 */
@WebMvcTest(SkuController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SkuController Unit Tests")
class SkuControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private SkuService skuService;
    
    @MockitoBean
    private SkuDTOMapper skuDTOMapper;
    
    @Test
	@DisplayName("POST /api/products/{productId}/skus - Create SKU successfully returns 201 Created")
    void createSku_Success_Returns201() throws Exception {
        // Given
        Sku mockSku = mock(Sku.class);
        when(mockSku.getSkuId()).thenReturn("sku-123");
        
        SkuResponseDTO responseDTO = mock(SkuResponseDTO.class);
        
		when(skuDTOMapper.toDomain(any(SkuCreateDTO.class), any(String.class))).thenReturn(mockSku);
		when(skuService.createSku(any(Sku.class))).thenReturn(mockSku);
        when(skuDTOMapper.toResponseDTO(mockSku)).thenReturn(responseDTO);
        
        // When & Then
		mockMvc.perform(post("/api/products/{productId}/skus", "prod-123")
				.header("X-Shop-Id", "shop-1")
				.header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
				.content("{\"specCombination\":\"color:red;size:M\",\"price\":99.99}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", "/api/skus/sku-123"));
        
        verify(skuService).createSku(any(Sku.class));
    }
    
    @Test
    @DisplayName("PUT /api/skus/{id} - Update SKU successfully returns 200 OK")
    void updateSku_Success_Returns200() throws Exception {
        // Given
        String skuId = "sku-123";
        
        Sku mockSku = mock(Sku.class);
        SkuResponseDTO responseDTO = mock(SkuResponseDTO.class);
		when(skuService.getSku(any(SkuId.class))).thenReturn(Optional.of(mockSku));
        when(skuService.updateSku(any(SkuId.class), any(Sku.class))).thenReturn(mockSku);
        when(skuDTOMapper.toResponseDTO(mockSku)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(put("/api/skus/{id}", skuId)
				.header("X-Shop-Id", "shop-1")
				.header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
				.content("{\"specCombination\":\"color:red;size:M\",\"price\":99.99}"))
            .andExpect(status().isOk());
        
        verify(skuService).updateSku(any(SkuId.class), any(Sku.class));
    }
    
    @Test
    @DisplayName("PUT /api/skus/{id} - SKU not found returns 404")
    void updateSku_NotFound_Returns404() throws Exception {
        // Given
        String skuId = "non-existent";
		when(skuService.getSku(any(SkuId.class))).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(put("/api/skus/{id}", skuId)
				.header("X-Shop-Id", "shop-1")
				.header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
				.content("{\"specCombination\":\"color:red;size:M\",\"price\":99.99}"))
			.andExpect(status().isNotFound());
    }
    
    @Test
    @org.junit.jupiter.api.Tag("ep:product:GET:/api/skus/{skuId}")
    @DisplayName("GET /api/skus/{id} - Get SKU successfully returns 200 OK")
    void getSku_Success_Returns200() throws Exception {
        // Given
        String skuId = "sku-123";
        
        Sku mockSku = mock(Sku.class);
        SkuResponseDTO responseDTO = mock(SkuResponseDTO.class);
        
        when(skuService.getSku(any(SkuId.class))).thenReturn(Optional.of(mockSku));
        when(skuDTOMapper.toResponseDTO(mockSku)).thenReturn(responseDTO);
        
        // When & Then
		mockMvc.perform(get("/api/skus/{id}", skuId)
				.header("X-Shop-Id", "shop-1"))
            .andExpect(status().isOk());
        
        verify(skuService).getSku(any(SkuId.class));
    }
    
    @Test
    @org.junit.jupiter.api.Tag("ep:product:GET:/api/skus/{skuId}")
    @DisplayName("GET /api/skus/{id} - SKU not found returns 404")
    void getSku_NotFound_Returns404() throws Exception {
        // Given
        String skuId = "non-existent";
        
        when(skuService.getSku(any(SkuId.class))).thenReturn(Optional.empty());
        
        // When & Then
		mockMvc.perform(get("/api/skus/{id}", skuId)
				.header("X-Shop-Id", "shop-1"))
			.andExpect(status().isNotFound());
    }
    
    @Test
    @DisplayName("PUT /api/skus/{id}/attributes - Update attributes successfully returns 200 OK")
    void updateAttributes_Success_Returns200() throws Exception {
        // Given
        String skuId = "sku-123";
        
        Sku mockSku = mock(Sku.class);
        SkuResponseDTO responseDTO = mock(SkuResponseDTO.class);
		when(skuService.getSku(any(SkuId.class))).thenReturn(Optional.of(mockSku));
        when(skuService.updateSku(any(SkuId.class), any(Sku.class))).thenReturn(mockSku);
        when(skuDTOMapper.toResponseDTO(mockSku)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(put("/api/skus/{id}/attributes", skuId)
				.header("X-Shop-Id", "shop-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributes\":{\"livePrice\":\"88.88\"}}"))
            .andExpect(status().isOk());
        
        verify(skuService).updateSku(any(SkuId.class), any(Sku.class));
    }
}
