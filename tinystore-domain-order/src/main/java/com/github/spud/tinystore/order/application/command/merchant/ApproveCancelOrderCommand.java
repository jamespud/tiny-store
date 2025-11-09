package com.github.spud.tinystore.order.application.command.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 取消审批通过命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class ApproveCancelOrderCommand {

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

	/**
	 * 备注
	 */
	private String remark;
}