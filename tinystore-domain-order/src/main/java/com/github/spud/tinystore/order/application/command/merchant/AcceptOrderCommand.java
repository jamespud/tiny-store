package com.github.spud.tinystore.order.application.command.merchant;

import lombok.Data;

@Data
public class AcceptOrderCommand {

	private String orderId;
	private String merchantId;
}
