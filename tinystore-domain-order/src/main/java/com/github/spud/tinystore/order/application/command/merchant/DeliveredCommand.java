package com.github.spud.tinystore.order.application.command.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * 物流妥投命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class DeliveredCommand {
	/**
	 * 订单ID
	 */
	private UUID orderId;

	/**
	 * 运单号
	 */
	private String trackingNo;

	/**
	 * 妥投时间戳
	 */
	private Long deliveredAt;

	/**
	 * 来源
	 */
	private String source;

	/**
	 * 事件ID（幂等键）
	 */
	private String eventId;
}