package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Quote 请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionQuoteRequest {
    private String buyerId;
    private String shopId;
    private Long itemsTotalCents;
    private List<LineItem> lines;
    private String couponCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String skuId;
        private Integer quantity;
        private Long priceCents;
    }
}
