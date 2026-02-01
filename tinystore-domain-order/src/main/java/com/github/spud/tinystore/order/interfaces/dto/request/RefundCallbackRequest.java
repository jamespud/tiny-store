package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 退款结果通知请求
 */
@Data
public class RefundCallbackRequest {
    private String refundId;
    private String refundStatus;
    private Long refundAmountCents;
    private String reason;
    private String traceId;
}
