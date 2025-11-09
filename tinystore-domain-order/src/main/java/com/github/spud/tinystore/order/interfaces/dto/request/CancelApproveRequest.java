package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.merchant.ApproveCancelOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家同意取消请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelApproveRequest {

	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private String orderId;

	/**
	 * 操作员ID
	 */
	@NotBlank(message = "操作员ID不能为空")
	@Size(max = 64)
	private String operatorId;

	/**
	 * 幂等键
	 */
	@NotBlank(message = "幂等键不能为空")
	@Size(max = 100)
	private String idempotencyKey;

	/**
	 * 备注
	 */
	@Size(max = 256)
	private String remark;

	public ApproveCancelOrderCommand toCommand() {
		return new ApproveCancelOrderCommand(orderId, operatorId, idempotencyKey, remark);
	}
}