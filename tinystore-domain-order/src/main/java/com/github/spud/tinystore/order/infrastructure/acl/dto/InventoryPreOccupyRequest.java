package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 PreOccupy 请求 DTO（对齐 StockPreOccupyRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryPreOccupyRequest {
    private String shopId;
    private String tradeId;
    private Long expiresAtEpochMs;
    private List<LineItem> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        private String skuId;
        private Integer quantity;
    }
}
