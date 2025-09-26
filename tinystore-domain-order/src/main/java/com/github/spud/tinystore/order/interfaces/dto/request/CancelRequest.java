package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.user.CancelOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 取消订单请求DTO
 *
 * @author Spud
 * @date 2025/8/28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 取消原因代码
	 */
	@Size(max = 64)
	private String reasonCode;

	/**
	 * 客户端请求ID（用于幂等控制）
	 */
	@Size(max = 64)
	private String clientRequestId;

	/**
	 * 备注
	 */
	@Size(max = 256)
	private String remark;

	/**
	 * 幂等键
	 */
	@NotBlank(message = "幂等键不能为空")
	@Size(max = 100)
	private String idempotencyKey;

	public CancelOrderCommand toCommand() {
		return new CancelOrderCommand(orderId, reasonCode, clientRequestId, remark, idempotencyKey);
	}
}
