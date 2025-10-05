package com.github.spud.tinystore.product.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class PricingContextDTO {
    
    @NotBlank(message = "SKU ID is required")
    private String skuId;
    
    private Map<String, String> userTags;
    
    private String channel;
    
    private String userId;
}
