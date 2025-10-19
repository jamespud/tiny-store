package com.github.spud.tinystore.order.application.command;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

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