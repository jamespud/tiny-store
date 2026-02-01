package com.github.spud.tinystore.order.interfaces.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 支付成功回调请求
 * 兼容旧字段 paymentId 与新字段 paymentIntentId
 */
@Data
public class PaymentCallbackRequest {
    
    /**
     * 支付单号（新字段）
     * 兼容旧字段 paymentId
     */
    @JsonAlias("paymentId")
    private String paymentIntentId;
    
    private Long amountCents;
    private String traceId;
}
