package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Quote 请求 DTO（对齐 CheckoutQuoteRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionQuoteRequest {
    private String userId;
    private String traceId;
    private String addressId;
    private List<LineItem> lines;
    private AppliedIntent appliedIntent;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        private String skuId;
        private String shopId;
        private Integer quantity;
        private Long baseUnitPriceCents;
        private Long weightGrams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AppliedIntent {
        private List<String> platformCouponIds;
        private Map<String, List<String>> shopCouponIdsByShop;

        public List<String> getPlatformCouponIds() {
            return platformCouponIds == null ? Collections.emptyList() : platformCouponIds;
        }

        public Map<String, List<String>> getShopCouponIdsByShop() {
            return shopCouponIdsByShop == null ? Collections.emptyMap() : shopCouponIdsByShop;
        }
    }
}
