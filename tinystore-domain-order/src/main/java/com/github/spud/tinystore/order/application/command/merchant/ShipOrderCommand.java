package com.github.spud.tinystore.order.application.command.merchant;

import com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 商家发货命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class ShipOrderCommand {
	/**
	 * 订单ID
	 */
	private String orderId;

	/**
	 * 物流信息
	 */
	private ShipOrderRequest.LogisticsInfo logistics;

	/**
	 * 发货商品明细（可选）
	 */
	private List<ShipOrderRequest.ShipItem> items;

	/**
	 * 操作员ID
	 */
	private String operatorId;

	/**
	 * 幂等键
	 */
	private String idempotencyKey;
}