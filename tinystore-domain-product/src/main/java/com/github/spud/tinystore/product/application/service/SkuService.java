package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SkuService - Application service for SKU management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuService {
    
    private final SkuRepository skuRepository;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    /**
     * Create a new SKU
     */
    @Transactional
    @CacheEvict(value = "skus", allEntries = true)
    public Sku createSku(Sku sku) {
        String tenantId = tenantContext.getTenantId();
        log.info("Creating SKU: {} for product: {} tenant: {}", sku.getSkuId(), sku.getProductId(), tenantId);
        
        // Validate product exists
        // TODO: Add ProductRepository dependency and validation
        
        return skuRepository.save(sku);
    }
    
    /**
     * Update SKU attributes
     */
    @Transactional
    @CacheEvict(value = "skus", key = "#skuId.value")
    public Sku updateSku(SkuId skuId, Sku updatedSku) {
        String tenantId = tenantContext.getTenantId();
        log.info("Updating SKU: {} for tenant: {}", skuId, tenantId);
        
        Optional<Sku> existing = skuRepository.findById(skuId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("SKU not found: " + skuId);
        }
        
        Sku sku = existing.get();
        
        // Update attributes
        sku.setAttributes(updatedSku.getAttributes());
        sku.setBarCode(updatedSku.getBarCode());
        
        return skuRepository.save(sku);
    }
    
    /**
     * Get SKU by ID (with caching)
     */
    @Cacheable(value = "skus", key = "#skuId.value")
    public Optional<Sku> getSku(SkuId skuId) {
        String tenantId = tenantContext.getTenantId();
        log.debug("Getting SKU: {} for tenant: {}", skuId, tenantId);
        
        return skuRepository.findById(skuId);
    }
    
    /**
     * Find all SKUs for a product
     */
    @Cacheable(value = "product-skus", key = "#productId.value")
    public List<Sku> getSkusByProduct(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        log.debug("Getting SKUs for product: {} tenant: {}", productId, tenantId);
        
        return skuRepository.findByProductId(productId);
    }
    
    /**
     * Disable SKU
     */
    @Transactional
    @CacheEvict(value = {"skus", "product-skus"}, allEntries = true)
    public void disableSku(SkuId skuId) {
        String tenantId = tenantContext.getTenantId();
        log.info("Disabling SKU: {} for tenant: {}", skuId, tenantId);
        
        Optional<Sku> existing = skuRepository.findById(skuId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("SKU not found: " + skuId);
        }
        
        Sku sku = existing.get();
        sku.disable(); // Domain behavior
        
        skuRepository.save(sku);
    }
    
    /**
     * Delete SKU
     */
    @Transactional
    @CacheEvict(value = {"skus", "product-skus"}, allEntries = true)
    public void deleteSku(SkuId skuId) {
        String tenantId = tenantContext.getTenantId();
        log.info("Deleting SKU: {} for tenant: {}", skuId, tenantId);
        
        skuRepository.delete(skuId);
    }
}
