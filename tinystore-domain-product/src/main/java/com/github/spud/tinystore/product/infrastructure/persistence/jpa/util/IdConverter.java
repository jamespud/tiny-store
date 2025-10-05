package com.github.spud.tinystore.product.infrastructure.persistence.jpa.util;

import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;

/**
 * IdConverter - Utility for converting between domain IDs and JPA Long IDs
 */
public final class IdConverter {
    
    private IdConverter() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Convert ProductId to Long for JPA queries
     * Strategy: Use hashCode for stable mapping
     */
    public static Long toLong(ProductId productId) {
        if (productId == null) {
            return null;
        }
        // Use absolute value of hashCode to ensure positive Long
        return Math.abs((long) productId.getId().hashCode());
    }
    
    /**
     * Convert String SKU ID to Long for JPA queries
     */
    public static Long skuIdToLong(String skuId) {
        if (skuId == null) {
            return null;
        }
        return Math.abs((long) skuId.hashCode());
    }
    
    /**
     * Convert Long to ProductId
     * Note: This is a reconstruction method for domain objects
     */
    public static ProductId toProductId(Long id) {
        if (id == null) {
            return null;
        }
        // For reconstruction, we use the entity's actual string ID from database
        // This method is mainly for type conversion in queries
        return ProductId.of(String.valueOf(id));
    }
}
