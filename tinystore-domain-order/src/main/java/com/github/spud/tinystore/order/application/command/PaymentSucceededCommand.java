package com.github.spud.tinystore.order.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付成功回写命令
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSucceededCommand {
    private String paymentId;
    private String tradeId;
    private Long paidAmountCents;
    private String traceId;
}
