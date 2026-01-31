package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.model.valueobject.*;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SkuServiceTest - Unit tests for SkuService
 */
@ExtendWith(MockitoExtension.class)
class SkuServiceTest {
    
    @Mock
    private SkuRepository skuRepository;
    
    @Mock
    private ShopRepositoryConfig.ShopContext shopContext;
    
    @InjectMocks
    private SkuService skuService;
    
    private ProductId testProductId;
    private SkuId testSkuId;
    private Sku testSku;
    private Money testPrice;
    
    @BeforeEach
    void setUp() {
        testProductId = ProductId.of("test-product-1");
        testSkuId = SkuId.of("test-sku-1");
        testPrice = Money.of(new BigDecimal("99.99"));
        
        // Create test SKU using factory method
        testSku = Sku.create(
            testSkuId.getId(),
            testProductId.getId(),
            new SpecificationCombination(),
            "BAR123456"
        );
        testSku.setBasePrice(testPrice);
        
        // Mock tenant context
        when(shopContext.getShopId()).thenReturn("test-tenant");
    }
    
    @Test
    void shouldCreateSkuSuccessfully() {
        // Given
        when(skuRepository.save(any(Sku.class))).thenReturn(testSku);
        
        // When
        Sku result = skuService.createSku(testSku);
        
        // Then
        assertNotNull(result);
        assertEquals(testSkuId.getId(), result.getSkuId());
        assertEquals(testProductId.getId(), result.getProductId());
        verify(skuRepository, times(1)).save(testSku);
    }
    
    @Test
    void shouldUpdateSkuSuccessfully() {
        // Given
        Sku updatedSku = Sku.create(
            testSkuId.getId(),
            testProductId.getId(),
            new SpecificationCombination(),
            "BAR999999"
        );
        updatedSku.setAttributes(new SkuAttributePack());
        
        when(skuRepository.findById(testSkuId)).thenReturn(Optional.of(testSku));
        when(skuRepository.save(any(Sku.class))).thenReturn(testSku);
        
        // When
        Sku result = skuService.updateSku(testSkuId, updatedSku);
        
        // Then
        assertNotNull(result);
        verify(skuRepository, times(1)).findById(testSkuId);
        verify(skuRepository, times(1)).save(any(Sku.class));
    }
    
    @Test
    void shouldGetSkuById() {
        // Given
        when(skuRepository.findById(testSkuId)).thenReturn(Optional.of(testSku));
        
        // When
        Optional<Sku> result = skuService.getSku(testSkuId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(testSkuId.getId(), result.get().getSkuId());
        verify(skuRepository, times(1)).findById(testSkuId);
    }
    
    @Test
    void shouldGetSkusByProductId() {
        // Given
        SkuId sku2Id = SkuId.of("test-sku-2");
        Sku sku2 = Sku.create(
            sku2Id.getId(),
            testProductId.getId(),
            new SpecificationCombination(),
            "BAR789012"
        );
        
        List<Sku> expectedSkus = List.of(testSku, sku2);
        when(skuRepository.findByProductId(testProductId)).thenReturn(expectedSkus);
        
        // When
        List<Sku> result = skuService.getSkusByProduct(testProductId);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(testProductId.getId(), result.get(0).getProductId());
        assertEquals(testProductId.getId(), result.get(1).getProductId());
        verify(skuRepository, times(1)).findByProductId(testProductId);
    }
    
    @Test
    void shouldThrowExceptionWhenSkuNotFound() {
        // Given
        when(skuRepository.findById(testSkuId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            skuService.updateSku(testSkuId, testSku);
        });
        
        verify(skuRepository, times(1)).findById(testSkuId);
        verify(skuRepository, never()).save(any(Sku.class));
    }
    
    @Test
    void shouldDisableSkuSuccessfully() {
        // Given
        when(skuRepository.findById(testSkuId)).thenReturn(Optional.of(testSku));
        when(skuRepository.save(any(Sku.class))).thenReturn(testSku);
        
        // When
        skuService.disableSku(testSkuId);
        
        // Then
        verify(skuRepository, times(1)).findById(testSkuId);
        verify(skuRepository, times(1)).save(any(Sku.class));
        assertEquals(SkuStatus.DISABLED, testSku.getStatus());
    }
    
    @Test
    void shouldThrowExceptionWhenDisablingNonExistentSku() {
        // Given
        when(skuRepository.findById(testSkuId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            skuService.disableSku(testSkuId);
        });
        
        verify(skuRepository, times(1)).findById(testSkuId);
        verify(skuRepository, never()).save(any(Sku.class));
    }
}
