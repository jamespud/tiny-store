package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter.ProductRepositoryAdapter;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProductServiceTest - Unit tests for ProductService application layer
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {
    
    @Mock
    private ProductRepositoryAdapter productRepository;

	@Mock
	private TenantRepositoryConfig.TenantContext tenantContext;
    
    @InjectMocks
    private ProductService productService;
    
    private Product testProduct;
    private ProductId testProductId;
    
    @BeforeEach
    void setUp() {
        testProduct = mock(Product.class);
		testProductId = ProductId.of("prod-123");
		when(tenantContext.getTenantId()).thenReturn("tenant-1");
    }
    
    @Test
    @DisplayName("createProduct - Successfully creates new product")
    void createProduct_Success() {
        // Given
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        // When
        Product result = productService.createProduct(testProduct);
        
        // Then
        assertNotNull(result);
        verify(productRepository).save(testProduct);
    }
    
    @Test
    @DisplayName("updateProduct - Successfully updates existing product")
    void updateProduct_Success() {
        // Given
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
		when(testProduct.getStatus()).thenReturn(ProductStatus.DRAFT);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        Product updateData = mock(Product.class);
        
        // When
        Product result = productService.updateProduct(testProductId, updateData);
        
        // Then
        assertNotNull(result);
        verify(productRepository).findById(testProductId);
        verify(productRepository).save(any(Product.class));
    }
    
    @Test
    @DisplayName("updateProduct - Throws exception when product not found")
    void updateProduct_NotFound_ThrowsException() {
        // Given
        when(productRepository.findById(testProductId)).thenReturn(Optional.empty());
        
        Product updateData = mock(Product.class);
        
        // When & Then
		assertThrows(IllegalArgumentException.class, () -> {
            productService.updateProduct(testProductId, updateData);
        });
        
        verify(productRepository).findById(testProductId);
        verify(productRepository, never()).save(any(Product.class));
    }
    
    @Test
    @DisplayName("publishProduct - Successfully changes status to published")
    void publishProduct_Success() {
        // Given
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
		when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        // When
        Product result = productService.publishProduct(testProductId);
        
        // Then
        assertNotNull(result);
		verify(testProduct).publish();
        verify(productRepository).save(testProduct);
    }
    
    @Test
    @DisplayName("getProduct - Returns product when found")
    void getProduct_Found() {
        // Given
        when(productRepository.findById(testProductId)).thenReturn(Optional.of(testProduct));
        
        // When
        Optional<Product> result = productService.getProduct(testProductId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(testProduct, result.get());
        verify(productRepository).findById(testProductId);
    }
}
