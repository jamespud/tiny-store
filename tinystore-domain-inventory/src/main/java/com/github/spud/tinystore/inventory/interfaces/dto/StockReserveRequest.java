package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class StockReserveRequest {

    @NotBlank
    private String tradeId;

    @NotNull
    private Long expiresAtEpochMs;

    @NotEmpty
    @Valid
    private List<OrderLine> orderLines;
    
    public List<SkuLine> getAllSkuLines() {
        return orderLines.stream()
                .flatMap(orderLine -> orderLine.getSkuLines().stream())
                .toList();
    }

    @Data
    public static class OrderLine {

        @NotBlank
        private String shopId;

        @NotBlank
        private String orderId;

        @NotEmpty
        @Valid
        private List<SkuLine> skuLines;

        public boolean hasDuplicateSkus() {
            long uniqueSkuCount = skuLines.stream()
                    .map(SkuLine::getSkuId)
                    .distinct()
                    .count();
            return uniqueSkuCount < skuLines.size();
        }
    }

    @Data
    public static class SkuLine {
        @NotBlank
        private String skuId;

        @NotNull
        @Positive
        private Integer quantity;
    }

}
