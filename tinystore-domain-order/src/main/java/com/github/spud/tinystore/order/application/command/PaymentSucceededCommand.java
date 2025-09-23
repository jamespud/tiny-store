package com.github.spud.tinystore.order.application.command;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * Command for payment success callback
 */
@Data
@Builder
public class PaymentSucceededCommand {
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private boolean isDeposit;
    private boolean isFinalPayment;
    private String idempotencyKey; // Third-party callback ID or generated key
}