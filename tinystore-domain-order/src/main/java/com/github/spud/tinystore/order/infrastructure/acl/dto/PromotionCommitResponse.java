package com.github.spud.tinystore.order.infrastructure.acl.dto;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Commit 响应 DTO（对齐 CheckoutCommitResponse）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCommitResponse {
    private PromotionQuoteResponse.CheckoutResultStatus status;
    private String finalQuoteId;
    private PromotionQuoteResponse.PricingSnapshot snapshot;
    private List<PromotionQuoteResponse.ChangeReason> changeReasons;
    private String message;

    public List<PromotionQuoteResponse.ChangeReason> getChangeReasons() {
        return changeReasons == null ? Collections.emptyList() : changeReasons;
    }
}
