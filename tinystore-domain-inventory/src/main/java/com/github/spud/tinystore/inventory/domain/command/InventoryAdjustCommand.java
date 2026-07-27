package com.github.spud.tinystore.inventory.domain.command;

import com.github.spud.tinystore.inventory.domain.enums.AdjustmentReason;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InventoryAdjustCommand {
    private String idempotencyKey;
    private AdjustmentReason reason;
    private String referenceId;
    private String tradeId;
    private String traceId;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private String shopId;
        private String skuId;
        private long delta; // signed: +add / -subtract
    }
}
