package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Commit 请求 DTO（对齐 StockCommitRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCommitRequest {
    private String shopId;
    private String tradeId;
    private String payNo;
    private Long paidAtEpochMs;
    private List<String> preOccupyIds;
}
