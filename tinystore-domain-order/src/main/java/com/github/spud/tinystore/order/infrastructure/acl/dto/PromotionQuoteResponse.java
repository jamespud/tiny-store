package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Quote 响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionQuoteResponse {
    private Long discountAmountCents;
    private Long itemsTotalCents;
    private Long payableAmountCents;
    private String version;
}
