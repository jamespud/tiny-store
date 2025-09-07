package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
	 *
	 */
	@NotBlank(message = "幂等键不能为空")
	@Size(max = 100)
	private String idempotencyKey;
}
