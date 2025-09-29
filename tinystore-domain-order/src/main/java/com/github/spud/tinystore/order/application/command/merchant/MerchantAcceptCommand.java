package com.github.spud.tinystore.order.application.command.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商家接单命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class MerchantAcceptCommand {
	/**
	 * 订单ID
	 */
	private String orderId;

	/**
	 * 操作员ID
	 */
	private String operatorId;

	/**
	 * 幂等键
	 */
	private String idempotencyKey;
}