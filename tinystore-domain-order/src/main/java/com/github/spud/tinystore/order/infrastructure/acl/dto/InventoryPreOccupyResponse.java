package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 PreOccupy 响应 DTO（对齐 StockPreOccupyResponse）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryPreOccupyResponse {
    private Boolean success;
    private List<String> preOccupyIds;
    private List<String> lackSkuIds;
    private Long expiresAtEpochMs;
    private String message;

    public List<String> getPreOccupyIds() {
        return preOccupyIds == null ? Collections.emptyList() : preOccupyIds;
    }

    public List<String> getLackSkuIds() {
        return lackSkuIds == null ? Collections.emptyList() : lackSkuIds;
    }
}
