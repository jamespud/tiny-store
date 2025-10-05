package com.github.spud.tinystore.product.interfaces.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PricingResultDTO {
    private BigDecimal finalPrice;
    private BigDecimal originalPrice;
    private List<PriceAdjustmentDTO> adjustments;
    private Long skuVersion;
    private List<String> ruleVersions;
    private String priceSignature;
    
    @Data
    public static class PriceAdjustmentDTO {
        private String ruleCode;
        private String ruleName;
        private String adjustmentType;
        private BigDecimal adjustmentAmount;
        private String description;
    }
}
