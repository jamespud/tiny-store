package com.github.spud.tinystore.product.interfaces.rest;

import com.github.spud.tinystore.product.application.service.ProductService;
import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.interfaces.dto.ProductCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductUpdateDTO;
import com.github.spud.tinystore.product.interfaces.mapper.ProductDTOMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductControllerTest - Unit tests for ProductController REST endpoints
 * 
 * Tests HTTP semantics, status codes, response structure, and service integration.
 * Uses MockMvc for controller testing and Mockito for service mocking.
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController Unit Tests")
class ProductControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ProductService productService;
    
    @MockBean
    private ProductDTOMapper productDTOMapper;
    
    @Test
    @DisplayName("POST /api/products - Create product successfully returns 201 Created")
    void createProduct_Success_Returns201() throws Exception {
        // Given
        Product mockProduct = mock(Product.class);
        when(mockProduct.getProductId()).thenReturn(ProductId.of("prod-123"));
        
        ProductResponseDTO responseDTO = mock(ProductResponseDTO.class);
        when(responseDTO.getId()).thenReturn("prod-123");
        when(responseDTO.getName()).thenReturn("Test Product");
        
        when(productService.createProduct(any(Product.class))).thenReturn(mockProduct);
        when(productDTOMapper.toResponseDTO(mockProduct)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Product\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", "/api/products/prod-123"))
            .andExpect(jsonPath("$.productId").value("prod-123"))
            .andExpect(jsonPath("$.name").value("Test Product"));
        
        verify(productService).createProduct(any(Product.class));
    }
    
    @Test
    @DisplayName("POST /api/products - Invalid data returns 400 Bad Request")
    void createProduct_InvalidData_Returns400() throws Exception {
        // When & Then - Empty request body
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("PUT /api/products/{id} - Update product successfully returns 200 OK")
    void updateProduct_Success_Returns200() throws Exception {
        // Given
        String productId = "prod-123";
        
        Product mockProduct = mock(Product.class);
        when(mockProduct.getProductId()).thenReturn(ProductId.of(productId));
        
        ProductResponseDTO responseDTO = mock(ProductResponseDTO.class);
        when(responseDTO.getId()).thenReturn(productId);
        when(responseDTO.getName()).thenReturn("Updated Product");
        
        when(productService.updateProduct(any(ProductId.class), any(Product.class)))
            .thenReturn(mockProduct);
        when(productDTOMapper.toResponseDTO(mockProduct)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(put("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Product\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(productId))
            .andExpect(jsonPath("$.name").value("Updated Product"));
        
        verify(productService).updateProduct(any(ProductId.class), any(Product.class));
    }
    
    @Test
    @DisplayName("PUT /api/products/{id} - Product not found returns 404 Not Found")
    void updateProduct_NotFound_Returns404() throws Exception {
        // Given
        String productId = "non-existent";
        
        when(productService.updateProduct(any(ProductId.class), any(Product.class)))
            .thenThrow(new java.util.NoSuchElementException("Product not found"));
        
        // When & Then
        mockMvc.perform(put("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Product\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").exists());
    }
    
    @Test
    @DisplayName("POST /api/products/{id}/publish - Publish product successfully returns 200 OK")
    void publishProduct_Success_Returns200() throws Exception {
        // Given
        String productId = "prod-123";
        
        Product mockProduct = mock(Product.class);
        when(mockProduct.getProductId()).thenReturn(ProductId.of(productId));
        
        ProductResponseDTO responseDTO = mock(ProductResponseDTO.class);
        when(responseDTO.getId()).thenReturn(productId);
        
        when(productService.publishProduct(any(ProductId.class))).thenReturn(mockProduct);
        when(productDTOMapper.toResponseDTO(mockProduct)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(post("/api/products/{id}/publish", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(productId));
        
        verify(productService).publishProduct(any(ProductId.class));
    }
    
    @Test
    @DisplayName("DELETE /api/products/{id} - Archive product successfully returns 204 No Content")
    void archiveProduct_Success_Returns204() throws Exception {
        // Given
        String productId = "prod-123";
        
        // When & Then
        mockMvc.perform(delete("/api/products/{id}", productId))
            .andExpect(status().isNoContent());
        
        verify(productService).archiveProduct(any(ProductId.class));
    }
    
    @Test
    @DisplayName("GET /api/products/{id} - Get product successfully returns 200 OK")
    void getProduct_Success_Returns200() throws Exception {
        // Given
        String productId = "prod-123";
        
        Product mockProduct = mock(Product.class);
        when(mockProduct.getProductId()).thenReturn(ProductId.of(productId));
        
        ProductResponseDTO responseDTO = mock(ProductResponseDTO.class);
        when(responseDTO.getId()).thenReturn(productId);
        when(responseDTO.getName()).thenReturn("Test Product");
        
        when(productService.getProduct(any(ProductId.class)))
            .thenReturn(Optional.of(mockProduct));
        when(productDTOMapper.toResponseDTO(mockProduct)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(get("/api/products/{id}", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(productId))
            .andExpect(jsonPath("$.name").value("Test Product"));
        
        verify(productService).getProduct(any(ProductId.class));
    }
    
    @Test
    @DisplayName("GET /api/products/{id} - Product not found returns 404 Not Found")
    void getProduct_NotFound_Returns404() throws Exception {
        // Given
        String productId = "non-existent";
        
        when(productService.getProduct(any(ProductId.class)))
            .thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/api/products/{id}", productId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").exists());
    }
    
    @Test
    @DisplayName("PUT /api/products/{id}/tags - Update tags successfully returns 200 OK")
    void updateTags_Success_Returns200() throws Exception {
        // Given
        String productId = "prod-123";
        
        Product mockProduct = mock(Product.class);
        when(mockProduct.getProductId()).thenReturn(ProductId.of(productId));
        
        ProductResponseDTO responseDTO = mock(ProductResponseDTO.class);
        when(responseDTO.getId()).thenReturn(productId);
        
        when(productService.updateTags(any(ProductId.class), any())).thenReturn(mockProduct);
        when(productDTOMapper.toResponseDTO(mockProduct)).thenReturn(responseDTO);
        
        // When & Then
        mockMvc.perform(put("/api/products/{id}/tags", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"tag1\",\"tag2\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(productId));
        
        verify(productService).updateTags(any(ProductId.class), any());
    }
}
