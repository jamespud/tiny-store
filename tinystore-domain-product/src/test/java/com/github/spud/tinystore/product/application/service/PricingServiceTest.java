package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.model.valueobject.SpecificationCombination;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.domain.repository.PricingRuleRepository;
import com.github.spud.tinystore.product.domain.rules.PricingRule;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PricingServiceTest - Unit tests for PricingService
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {
    
    @Mock
    private PricingRuleRepository ruleRepository;
    
    @Mock
    private TenantRepositoryConfig.TenantContext tenantContext;
    
    @InjectMocks
    private PricingService pricingService;
    
    private SkuId testSkuId;
    private Sku testSku;
    private PricingContext testContext;
    private Money originalPrice;
    
    @BeforeEach
    void setUp() {
        testSkuId = SkuId.of("test-sku-1");
        ProductId testProductId = ProductId.of("test-product-1");
        
        // Create test SKU
        testSku = Sku.create(
            testSkuId.getId(),
            testProductId.getId(),
            new SpecificationCombination(),
            "BAR123456"
        );
        originalPrice = Money.of(new BigDecimal("100.00"));
        testSku.setBasePrice(originalPrice);
        
        // Create pricing context
        testContext = new PricingContext(testSku, Map.of("brand", "nike"), Instant.now());
        
        // Mock tenant context
        when(tenantContext.getTenantId()).thenReturn("test-tenant");
    }
    
    @Test
    void shouldCalculatePriceSuccessfully() {
        // Given
        PricingRule mockRule = mock(PricingRule.class);
        when(mockRule.matches(any(PricingContext.class))).thenReturn(true);
        when(mockRule.priority()).thenReturn(1);
        when(mockRule.exclusiveGroup()).thenReturn(null);
        
        when(ruleRepository.findActiveRulesByProduct(anyString(), anyString(), any(Instant.class)))
            .thenReturn(List.of(mockRule));
        when(ruleRepository.findAllActiveRules(anyString(), any(Instant.class)))
            .thenReturn(List.of());
        
        // When
        PricingResult result = pricingService.calculatePrice(testContext);
        
        // Then
        assertNotNull(result);
        assertEquals(originalPrice, result.basePrice());
        verify(ruleRepository, times(1)).findActiveRulesByProduct(anyString(), anyString(), any(Instant.class));
    }
    
    @Test
    void shouldHandleMultipleRules() {
        // Given
        PricingRule rule1 = mock(PricingRule.class);
        PricingRule rule2 = mock(PricingRule.class);
        
        when(rule1.matches(any(PricingContext.class))).thenReturn(true);
        when(rule1.priority()).thenReturn(1);
        when(rule1.exclusiveGroup()).thenReturn(null);
        
        when(rule2.matches(any(PricingContext.class))).thenReturn(true);
        when(rule2.priority()).thenReturn(2);
        when(rule2.exclusiveGroup()).thenReturn(null);
        
        when(ruleRepository.findActiveRulesByProduct(anyString(), anyString(), any(Instant.class)))
            .thenReturn(List.of(rule1, rule2));
        when(ruleRepository.findAllActiveRules(anyString(), any(Instant.class)))
            .thenReturn(List.of());
        
        // When
        PricingResult result = pricingService.calculatePrice(testContext);
        
        // Then
        assertNotNull(result);
        assertEquals(originalPrice, result.basePrice());
        verify(ruleRepository, times(1)).findActiveRulesByProduct(anyString(), anyString(), any(Instant.class));
        verify(ruleRepository, times(1)).findAllActiveRules(anyString(), any(Instant.class));
    }
    
    @Test
    void shouldReturnOriginalPriceWhenNoRulesApply() {
        // Given
        when(ruleRepository.findActiveRulesByProduct(anyString(), anyString(), any(Instant.class)))
            .thenReturn(List.of());
        when(ruleRepository.findAllActiveRules(anyString(), any(Instant.class)))
            .thenReturn(List.of());
        
        // When
        PricingResult result = pricingService.calculatePrice(testContext);
        
        // Then
        assertNotNull(result);
        assertEquals(originalPrice, result.basePrice());
        assertEquals(originalPrice, result.finalPrice());
        assertTrue(result.adjustments().isEmpty());
    }
    
    @Test
    void shouldHandleUserTagsInContext() {
        // Given
        List<String> userTags = List.of("vip", "premium");
        PricingContext contextWithTags = new PricingContext(
            testSku, 
            Map.of("userTags", userTags), 
            Instant.now()
        );
        
        PricingRule tagRule = mock(PricingRule.class);
        when(tagRule.matches(any(PricingContext.class))).thenReturn(true);
        when(tagRule.priority()).thenReturn(1);
        when(tagRule.exclusiveGroup()).thenReturn(null);
        
        when(ruleRepository.findActiveRulesByProduct(anyString(), anyString(), any(Instant.class)))
            .thenReturn(List.of());
        when(ruleRepository.findActiveRulesByTags(anyString(), anyList(), any(Instant.class)))
            .thenReturn(List.of(tagRule));
        when(ruleRepository.findAllActiveRules(anyString(), any(Instant.class)))
            .thenReturn(List.of());
        
        // When
        PricingResult result = pricingService.calculatePrice(contextWithTags);
        
        // Then
        assertNotNull(result);
        verify(ruleRepository, times(1)).findActiveRulesByTags(anyString(), anyList(), any(Instant.class));
    }
}

