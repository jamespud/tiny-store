package com.github.spud.tinystore.order.application.command;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * 转待履约命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class MoveToAwaitFulfillmentCommand {
	/**
	 * 订单ID
	 */
	private UUID orderId;

	/**
	 * 事件ID（幂等键）
	 */
	private String eventId;
}