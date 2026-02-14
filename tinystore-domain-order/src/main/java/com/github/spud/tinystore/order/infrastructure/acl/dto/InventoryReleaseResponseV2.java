package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory 域 release 响应 DTO V2 (对齐新 InventoryReleaseResponse)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReleaseResponseV2 {
    private Boolean success;
    private String message;

    public Boolean getSuccess() {
        return success != null && success;
    }
}
