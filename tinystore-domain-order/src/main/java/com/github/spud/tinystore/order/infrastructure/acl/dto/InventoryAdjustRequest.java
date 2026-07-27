package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryAdjustRequest {
    private String reason;        // "RESTOCK_REFUND"
    private String referenceId;   // refundId
    private String tradeId;
    private List<Item> items;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private String shopId;
        private String skuId;
        private Long delta;        // signed; +qty for refund
    }
}
