package com.github.spud.tinystore.order.application.command;

import com.github.spud.tinystore.order.domain.model.Money;
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
	private Money amount;
	private Long paidAt;
	private boolean isDeposit;
	private boolean isFinalPayment;
}
