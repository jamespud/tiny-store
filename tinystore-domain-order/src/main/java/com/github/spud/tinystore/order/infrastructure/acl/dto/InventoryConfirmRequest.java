package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for POST /api/inventory/reservations/confirm (ACL layer, order domain).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryConfirmRequest {
    private String paymentId;
    private String tradeId;
    private String orderId;
    private String traceId;
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
