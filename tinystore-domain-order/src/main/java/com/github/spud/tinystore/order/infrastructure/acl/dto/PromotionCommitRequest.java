package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Commit 请求 DTO（对齐 CheckoutCommitRequest）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCommitRequest {
    private String quoteId;
    private String tradeId;
    private String inputHash;
    private String payNo;
    private Long paidAt;
}
