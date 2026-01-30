package com.github.spud.tinystore.order.infrastructure.acl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promotion 域 Release 响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionReleaseResponse {
    private Boolean success;
}
