package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

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