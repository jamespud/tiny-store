package com.github.spud.tinystore.inventory.domain.command;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Canonical inventory reserve command.
 * <p>
 * Replaces the two-step "pre-occupy + commit" model.
 * A successful reserve creates a PRE_DEDUCTED reservation entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveCommand {

    private String idempotencyKey;
    private String orderId;
    private String tradeId;
    private String traceId;
    private OffsetDateTime expireAt;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String shopId;
        private String skuId;
        private int quantity;
    }
}
