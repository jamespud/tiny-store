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

    /** 回调时间戳（毫秒），用于签名载荷。 */
    private Long timestamp;

    /** 回调签名（HMAC-SHA256 hex，见 PaymentSignatureVerifier）。 */
    private String signature;
}
