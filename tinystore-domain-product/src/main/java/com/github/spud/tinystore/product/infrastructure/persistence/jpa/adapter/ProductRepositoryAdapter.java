package com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper.ProductMapper;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaProductRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ProductRepositoryAdapter - Adapts JPA repository to domain repository interface
 * 
 * Responsibilities:
 * - Implement domain ProductRepository interface
 * - Delegate to JpaProductRepository for persistence
 * - Use ProductMapper for entity/domain conversions
 * - Inject tenant ID from context for all operations
 */
@Repository
public class ProductRepositoryAdapter implements ProductRepository {
    
    private final JpaProductRepository jpaRepository;
    private final ProductMapper mapper;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    public ProductRepositoryAdapter(
            JpaProductRepository jpaRepository,
            ProductMapper mapper,
            TenantRepositoryConfig.TenantContext tenantContext) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }
    
    @Override
    public Product save(Product product) {
        String tenantId = tenantContext.getTenantId();
        ProductEntity entity = mapper.toEntity(product, tenantId);
        ProductEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
    
    @Override
    public Optional<Product> findById(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        // TODO: Convert ProductId to entity ID (Long) and implement lookup
        // This requires understanding ProductId structure
        return Optional.empty();
    }
    
    @Override
    public void delete(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        // TODO: Implement soft delete or hard delete based on requirements
        // Convert ProductId to entity ID first
        throw new UnsupportedOperationException("Delete not yet implemented");
    }
}

