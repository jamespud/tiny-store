package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustResponse {
    private boolean success;
    private String message;

    public static InventoryAdjustResponse ok() {
        return InventoryAdjustResponse.builder().success(true).message("ok").build();
    }

    public static InventoryAdjustResponse fail(String message) {
        return InventoryAdjustResponse.builder().success(false).message(message).build();
    }
}
