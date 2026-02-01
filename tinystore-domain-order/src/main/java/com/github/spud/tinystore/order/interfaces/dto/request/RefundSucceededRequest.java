package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 退款成功请求
 */
@Data
public class RefundSucceededRequest {
    private String refundId;
    private Long refundAmountCents;
    private String refundedAt;
    private String traceId;
}
