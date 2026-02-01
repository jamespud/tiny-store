package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Release 请求 DTO（对齐 StockReleaseRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReleaseRequest {
    private String shopId;
    private String tradeId;
    private String reason;
    private List<String> preOccupyIds;
}
