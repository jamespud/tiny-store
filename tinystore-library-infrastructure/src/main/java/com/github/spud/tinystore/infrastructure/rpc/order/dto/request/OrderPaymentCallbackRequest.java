package com.github.spud.tinystore.infrastructure.rpc.order.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单域支付回调 RPC 请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentCallbackRequest {
    
    /**
     * 支付单号（新字段）
     * 兼容旧字段 paymentId
     */
    @JsonAlias("paymentId")
    private String paymentIntentId;
    
    private Long amountCents;
    private String traceId;
}
