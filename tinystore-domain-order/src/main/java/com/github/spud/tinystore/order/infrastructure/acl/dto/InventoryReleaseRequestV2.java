package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Inventory 域 release 请求 DTO V2 (对齐新 InventoryReleaseRequest，按 SKU 回滚)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReleaseRequestV2 {
    private String orderId;
    private String reason;
    private List<OccupyPairDto> occupyPairs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OccupyPairDto {
        private String shopId;
        private String skuId;
        private String occupyId;
    }
}
