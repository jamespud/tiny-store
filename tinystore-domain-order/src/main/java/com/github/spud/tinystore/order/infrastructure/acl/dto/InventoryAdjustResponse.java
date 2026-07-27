package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryAdjustResponse {
    private Boolean success;
    private String message;
}
