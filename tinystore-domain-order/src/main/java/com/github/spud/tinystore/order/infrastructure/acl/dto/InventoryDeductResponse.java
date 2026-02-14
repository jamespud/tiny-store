package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Inventory 域 deduct 响应 DTO（对齐 DeductResponse）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDeductResponse {
    private Boolean success;
    private String message;
    @Builder.Default
    private List<OccupyPairDto> occupyPairs = Collections.emptyList();
    @Builder.Default
    private List<String> lackSkuIds = Collections.emptyList();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OccupyPairDto {
        private String shopId;
        private String skuId;
        private String occupyId;
    }

    public Boolean getSuccess() {
        return success != null && success;
    }
}
