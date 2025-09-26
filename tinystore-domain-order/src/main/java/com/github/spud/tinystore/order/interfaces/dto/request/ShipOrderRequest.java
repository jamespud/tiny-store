package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.merchant.ShipOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 商家发货请求DTO
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipOrderRequest {
	/**
	 * 订单ID
	 */
	@NotNull(message = "订单ID不能为空")
	private UUID orderId;

	/**
	 * 物流信息
	 */
	@NotNull(message = "物流信息不能为空")
	private LogisticsInfo logistics;

	/**
	 * 发货商品明细（可选，部分发货时使用）
	 */
	private List<ShipItem> items;

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

	public ShipOrderCommand toCommand() {
		return new ShipOrderCommand(orderId, logistics, items, operatorId, idempotencyKey);
	}

	/**
	 * 物流信息
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LogisticsInfo {
		/**
		 * 物流公司代码
		 */
		@NotBlank(message = "物流公司代码不能为空")
		private String companyCode;

		/**
		 * 运单号
		 */
		@NotBlank(message = "运单号不能为空")
		private String trackingNo;

		/**
		 * 物流公司名称
		 */
		private String companyName;
	}

	/**
	 * 发货商品项
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ShipItem {
		private String skuId;
		private Integer quantity;
	}
}