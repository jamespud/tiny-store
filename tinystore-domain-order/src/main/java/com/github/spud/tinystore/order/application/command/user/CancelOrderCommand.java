package com.github.spud.tinystore.order.application.command.user;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/6
 */
@AllArgsConstructor
@Data
public class CancelOrderCommand {

	/**
	 * 订单ID
	 */
	private UUID orderId;

	/**
	 * 取消原因代码
	 */
	private String reasonCode;

	/**
	 * 客户端请求ID（用于幂等控制）
	 */
	private String clientRequestId;

	/**
	 * 备注
	 */
	private String remark;

	/**
	 * 幂等键
	 */
	private String idempotencyKey;
}
