package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 Restock 响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRestockResponse {
    private Boolean success;
}
