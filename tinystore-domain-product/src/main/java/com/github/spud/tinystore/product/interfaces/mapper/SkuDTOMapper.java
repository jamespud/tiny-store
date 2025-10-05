package com.github.spud.tinystore.product.interfaces.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.interfaces.dto.SkuCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuUpdateDTO;
import org.springframework.stereotype.Component;

/**
 * SkuDTOMapper - Maps between Sku domain objects and DTOs
 * TODO: Complete implementation with proper domain object construction
 */
@Component
public class SkuDTOMapper {
    
    /**
     * Convert SkuCreateDTO to Sku domain object
     * TODO: Implement proper conversion using Sku.create() factory
     */
    public Sku toDomain(SkuCreateDTO dto, String productId) {
        // Placeholder - needs proper implementation
        throw new UnsupportedOperationException("SkuDTOMapper.toDomain not yet fully implemented");
    }
    
    /**
     * Convert Sku domain object to SkuResponseDTO
     */
    public SkuResponseDTO toResponseDTO(Sku sku) {
        SkuResponseDTO dto = new SkuResponseDTO();
        dto.setId(sku.getSkuId());
        dto.setProductId(sku.getProductId());
        dto.setSpecCombination(sku.getSpecs().toString());
        dto.setBarCode(sku.getBarCode());
        dto.setStatus(sku.getStatus().name());
        dto.setCreatedAt(sku.getCreateTime());
        // TODO: Set price, stock, attributes when supported
        return dto;
    }
    
    /**
     * Apply SkuUpdateDTO to existing Sku
     * TODO: Implement attribute updates
     */
    public void applyUpdate(Sku sku, SkuUpdateDTO dto) {
        if (dto.getBarCode() != null) {
            sku.setBarCode(dto.getBarCode());
        }
        // TODO: Update attributes, price, stock
    }
}

