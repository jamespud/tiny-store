package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Quote 响应 DTO（对齐 CheckoutQuoteResponse）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionQuoteResponse {
    private CheckoutResultStatus status;
    private String quoteId;
    private Long expiresAtEpochMs;
    private PricingSnapshot snapshot;
    private List<ChangeReason> changeReasons;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PricingSnapshot {
        private Long itemsTotalCents;
        private Long promotionDiscountTotalCents;
        private Long couponDiscountTotalCents;
        private Long shippingFeeCents;
        private Long payableCents;
        private SnapshotVersion version;
        private List<PricedLine> lines;
        private List<AppliedBenefit> appliedBenefits;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class PricedLine {
            private String skuId;
            private String shopId;
            private Integer quantity;
            private Long baseUnitPriceCents;
            private Long finalUnitPriceCents;
            private Long lineSubtotalCents;
            private Long lineDiscountAllocatedCents;
            private Long linePayableCents;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class AppliedBenefit {
            private String benefitType;
            private String benefitId;
            private String groupKey;
            private String lockId;
            private Long amountCents;
            private String ruleTrace;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class SnapshotVersion {
            private String pricingRulesVersion;
            private String shippingRulesVersion;
            private String inputHash;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChangeReason {
        private String reason;
        private String skuId;
    }

    public enum CheckoutResultStatus {
        OK,
        OK_WITH_CHANGE,
        REQUOTE_REQUIRED
    }

    public List<ChangeReason> getChangeReasons() {
        return changeReasons == null ? Collections.emptyList() : changeReasons;
    }
}
