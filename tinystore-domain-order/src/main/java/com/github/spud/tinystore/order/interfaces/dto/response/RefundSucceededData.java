package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退款成功响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSucceededData {
    private String caseId;
    private String refundId;
}
