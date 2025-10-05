package com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
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
     * @return ProductEntity for persistence
     */
    public ProductEntity toEntity(Product product) {
        // TODO: Implement mapping logic
        throw new UnsupportedOperationException("ProductMapper.toEntity not yet implemented");
    }
    
    /**
     * Convert ProductEntity to domain Product
     * 
     * @param entity ProductEntity from database
     * @return Domain product aggregate
     */
    public Product toDomain(ProductEntity entity) {
        // TODO: Implement mapping logic
        throw new UnsupportedOperationException("ProductMapper.toDomain not yet implemented");
    }
}
