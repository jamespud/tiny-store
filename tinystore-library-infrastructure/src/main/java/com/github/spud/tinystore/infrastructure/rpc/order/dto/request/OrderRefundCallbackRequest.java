package com.github.spud.tinystore.infrastructure.rpc.order.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单域退款回调 RPC 请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundCallbackRequest {
    private String refundId;
    private String refundStatus;
    private Long refundAmountCents;
    private String reason;
    private String traceId;
}
