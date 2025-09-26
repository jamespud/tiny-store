package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.merchant.MerchantAcceptCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 商家接单请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAcceptRequest {
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

	public MerchantAcceptCommand toCommand() {
		return new MerchantAcceptCommand(orderId, operatorId, idempotencyKey);
	}
}