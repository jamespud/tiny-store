package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InventoryAdjustRequest {
    @NotBlank private String reason;       // AdjustmentReason code, e.g. RESTOCK_REFUND
    @NotBlank private String referenceId;  // e.g. refundId
    private String tradeId;

    @NotEmpty @Valid
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        @NotBlank private String shopId;
        @NotBlank private String skuId;
        @NotNull private Long delta;        // signed
    }
}
