package com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.*;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import org.springframework.stereotype.Component;

/**
 * ProductMapper - Maps between Product domain model and ProductEntity
 * 
 * Mapping responsibilities:
 * - Convert domain Product aggregate to ProductEntity for persistence
 * - Convert ProductEntity to domain Product aggregate for retrieval
 * - Handle value object conversions (ProductId, Status, etc.)
 */
@Component
public class ProductMapper {
    
    /**
     * Convert domain Product to ProductEntity
     * 
     * @param product Domain product aggregate
     * @param tenantId Tenant identifier
     * @return ProductEntity for persistence
     */
    public ProductEntity toEntity(Product product, String tenantId) {
        if (product == null) {
            return null;
        }
        
        ProductEntity entity = new ProductEntity();
        entity.setTenantId(tenantId);
        entity.setName(product.getName());
        entity.setStatus(product.getStatus() != null ? product.getStatus().name() : ProductStatus.DRAFT.name());
        entity.setCategoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null);
        
        // If product has an ID (update scenario), try to preserve it
        // Note: ProductId is a value object, need to extract the actual ID
        if (product.getProductId() != null) {
            // Assuming ProductId has a way to get the string representation
            // This is a placeholder - adjust based on actual ProductId implementation
        }
        
        return entity;
    }
    
    /**
     * Convert ProductEntity to domain Product
     * 
     * @param entity ProductEntity from database
     * @return Domain product aggregate
     */
    public Product toDomain(ProductEntity entity) {
        if (entity == null) {
            return null;
        }
        
        // This is a simplified mapping - actual implementation needs to reconstruct
        // the full Product aggregate with all value objects
        // Using reflection or builder pattern based on Product's factory methods
        
        throw new UnsupportedOperationException(
            "ProductMapper.toDomain requires Product factory method - implement based on actual Product API");
    }
    
    /**
     * Update entity from domain product (for update operations)
     * 
     * @param entity Existing entity to update
     * @param product Domain product with new values
     */
    public void updateEntity(ProductEntity entity, Product product) {
        if (entity == null || product == null) {
            return;
        }
        
        entity.setName(product.getName());
        entity.setStatus(product.getStatus().name());
        entity.setCategoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null);
    }
}

