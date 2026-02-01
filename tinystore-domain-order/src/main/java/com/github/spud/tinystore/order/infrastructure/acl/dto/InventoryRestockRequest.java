package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Restock 请求 DTO（对齐 StockRestockRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRestockRequest {
    private String shopId;
    private String tradeId;
    private String refundId;
    private List<LineItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        private String skuId;
        private Integer quantity;
    }
}
