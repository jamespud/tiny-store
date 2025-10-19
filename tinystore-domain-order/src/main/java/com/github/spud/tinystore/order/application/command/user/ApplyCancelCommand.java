package com.github.spud.tinystore.order.application.command.user;

import lombok.Builder;
import lombok.Data;

/**
 * Command to apply for cancellation
 */
@Data
@Builder
public class ApplyCancelCommand {

	private String orderId;
	private String buyerId;
	private String reason;
}