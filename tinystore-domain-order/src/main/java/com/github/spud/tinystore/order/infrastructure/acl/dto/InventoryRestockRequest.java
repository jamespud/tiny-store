package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Restock 请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRestockRequest {
    private String refundId;
    private String orderId;
}
