package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

/**
 * Command for refund success callback
 */
@Data
@Builder
public class RefundSucceededCommand {
	private String orderId;
	private String refundId;
	private String idempotencyKey;
}