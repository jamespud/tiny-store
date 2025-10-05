package com.github.spud.tinystore.product.interfaces.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.interfaces.dto.ProductCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductUpdateDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ProductDTOMapper - Maps between Product domain objects and DTOs
 * TODO: Complete implementation with proper domain object construction
 */
@Component
public class ProductDTOMapper {
    
    /**
     * Convert ProductCreateDTO to Product domain object
     * TODO: Implement proper conversion using Product.create() factory
     */
    public Product toDomain(ProductCreateDTO dto) {
        // Placeholder - needs proper implementation
        throw new UnsupportedOperationException("ProductDTOMapper.toDomain not yet fully implemented");
    }
    
    /**
     * Convert Product domain object to ProductResponseDTO
     */
    public ProductResponseDTO toResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getProductId().getId());
        dto.setName(product.getName());
        dto.setCategoryId(product.getCategory().getCategoryId());
        dto.setStatus(product.getStatus().name());
        dto.setTags(List.of()); // TODO: Get from Product when supported
        dto.setCreatedAt(product.getCreateTime());
        dto.setUpdatedAt(product.getUpdateTime());
        return dto;
    }
}

