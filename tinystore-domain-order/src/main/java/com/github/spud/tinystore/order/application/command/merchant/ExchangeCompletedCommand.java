package com.github.spud.tinystore.order.application.command.merchant;

import lombok.Builder;
import lombok.Data;

/**
 * Command for exchange completion
 */
@Data
@Builder
public class ExchangeCompletedCommand {

	private String orderId;
	private String exchangeId;
}