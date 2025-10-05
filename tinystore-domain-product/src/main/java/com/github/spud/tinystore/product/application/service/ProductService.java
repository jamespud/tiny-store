package com.github.spud.tinystore.product.application.service;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
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
 * ProductService - Application service for Product aggregate
 * Coordinates domain logic, persistence, and caching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    /**
     * Create a new product (Draft status)
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public Product createProduct(Product product) {
        String tenantId = tenantContext.getTenantId();
        log.info("Creating product for tenant: {}, productId: {}", tenantId, product.getProductId());
        
        return productRepository.save(product);
    }
    
    /**
     * Update product attributes (only in DRAFT or OFFLINE status)
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId.value")
    public Product updateProduct(ProductId productId, Product updatedProduct) {
        String tenantId = tenantContext.getTenantId();
        log.info("Updating product: {} for tenant: {}", productId, tenantId);
        
        Optional<Product> existing = productRepository.findById(productId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        
        Product product = existing.get();
        
        // Domain logic: validate modifiable status
        if (product.getStatus() != ProductStatus.DRAFT && product.getStatus() != ProductStatus.OFFLINE) {
            throw new IllegalArgumentException("Only DRAFT or OFFLINE products can be modified");
        }
        
        // Update attributes via domain method
        product.updateAttributes(updatedProduct.getBaseAttributes());
        
        return productRepository.save(product);
    }
    
    /**
     * Publish product (transition to PUBLISHED status)
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId.value")
    public Product publishProduct(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        log.info("Publishing product: {} for tenant: {}", productId, tenantId);
        
        Optional<Product> existing = productRepository.findById(productId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        
        Product product = existing.get();
        
        // Domain logic: transition to published
        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new IllegalArgumentException("Only DRAFT products can be published");
        }
        
        // TODO: Trigger domain event ProductPublishedEvent
        // Update status (assuming setter exists or use reflection)
        // For now, save and return
        return productRepository.save(product);
    }
    
    /**
     * Archive product (soft delete)
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId.value")
    public void archiveProduct(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        log.info("Archiving product: {} for tenant: {}", productId, tenantId);
        
        Optional<Product> existing = productRepository.findById(productId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        
        productRepository.delete(productId);
    }
    
    /**
     * Get product by ID (with caching)
     */
    @Cacheable(value = "products", key = "#productId.value")
    public Optional<Product> getProduct(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        log.debug("Getting product: {} for tenant: {}", productId, tenantId);
        
        return productRepository.findById(productId);
    }
    
    /**
     * Update product tags
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId.value")
    public Product updateTags(ProductId productId, List<String> tags) {
        String tenantId = tenantContext.getTenantId();
        log.info("Updating tags for product: {} for tenant: {}", productId, tenantId);
        
        Optional<Product> existing = productRepository.findById(productId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        
        Product product = existing.get();
        
        // TODO: Add updateTags method to Product aggregate
        // For now, just save
        return productRepository.save(product);
    }
}
