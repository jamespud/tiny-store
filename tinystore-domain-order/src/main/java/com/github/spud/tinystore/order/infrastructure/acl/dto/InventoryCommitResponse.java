package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Commit 响应 DTO（对齐 StockCommitResponse）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCommitResponse {
    private Boolean success;
    private String message;
}
