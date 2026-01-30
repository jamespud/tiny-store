package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Commit 请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCommitRequest {
    private String quoteVersion;
    private String buyerId;
    private String shopId;
    private String couponCode;
}
