package com.github.spud.tinystore.inventory.domain.command;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 库存扣减命令
 */
@Data
@Builder
public class InventoryDeductCommand {

    private String orderId;
    private String idempotencyKey;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private String shopId;
        private String skuId;
        private int quantity;
    }
}
