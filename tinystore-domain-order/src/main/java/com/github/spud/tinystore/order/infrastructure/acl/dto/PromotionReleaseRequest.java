package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Release 请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionReleaseRequest {
    private String quoteId;
    private String orderNo;
    private String reason;
}
