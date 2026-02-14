package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseResponse {

    private boolean success;
    private String message;

    public static InventoryReleaseResponse ok(String message) {
        return InventoryReleaseResponse.builder().success(true).message(message).build();
    }

    public static InventoryReleaseResponse fail(String message) {
        return InventoryReleaseResponse.builder().success(false).message(message).build();
    }
}
