package com.github.spud.tinystore.product.interfaces.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.interfaces.dto.PricingContextDTO;
import com.github.spud.tinystore.product.interfaces.dto.PricingResultDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PricingDTOMapper - Maps between Pricing domain objects and DTOs
 * TODO: Complete implementation with proper Money type conversion
 */
@Component
public class PricingDTOMapper {
    
    /**
     * Convert PricingContextDTO to PricingContext domain object
     */
    public PricingContext toDomain(PricingContextDTO dto, Sku sku) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Add user tags to attributes if present
        if (dto.getUserTags() != null && !dto.getUserTags().isEmpty()) {
            attributes.put("userTags", List.copyOf(dto.getUserTags().values()));
        }
        
        // Add channel and userId
        if (dto.getChannel() != null) {
            attributes.put("channel", dto.getChannel());
        }
        if (dto.getUserId() != null) {
            attributes.put("userId", dto.getUserId());
        }
        
        return new PricingContext(sku, attributes, Instant.now());
    }
    
    /**
     * Convert PricingResult domain object to PricingResultDTO
     * TODO: Fix Money type conversion when Money class structure is known
     */
    public PricingResultDTO toResponseDTO(PricingResult result) {
        PricingResultDTO dto = new PricingResultDTO();
        
        // Placeholder values - need proper Money type conversion
        dto.setOriginalPrice(BigDecimal.ZERO); // TODO: result.basePrice()
        dto.setFinalPrice(BigDecimal.ZERO);    // TODO: result.finalPrice()
        
        // Convert adjustments
        List<PricingResultDTO.PriceAdjustmentDTO> adjustmentDTOs = result.adjustments().stream()
                .map(adj -> {
                    PricingResultDTO.PriceAdjustmentDTO adjDTO = new PricingResultDTO.PriceAdjustmentDTO();
                    adjDTO.setRuleCode(adj.ruleCode);
                    adjDTO.setDescription(adj.description);
                    adjDTO.setAdjustmentAmount(BigDecimal.ZERO); // TODO: adj.delta
                    return adjDTO;
                })
                .collect(Collectors.toList());
        
        dto.setAdjustments(adjustmentDTOs);
        
        return dto;
    }
}

