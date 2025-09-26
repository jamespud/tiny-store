package com.github.spud.tinystore.order.application.command.user;

import com.github.spud.tinystore.order.interfaces.dto.request.AfterSaleApplyRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 售后申请命令
 *
 * @author Spud
 * @date 2025/9/22
 */
@Data
@AllArgsConstructor
public class AfterSaleApplyCommand {
	/**
	 * 订单ID
	 */
	private UUID orderId;

	/**
	 * 用户ID
	 */
	private String userId;

	/**
	 * 售后类型
	 */
	private AfterSaleApplyRequest.AfterSaleType type;

	/**
	 * 原因代码
	 */
	private String reasonCode;

	/**
	 * 退款金额
	 */
	private BigDecimal amount;

	/**
	 * 退货商品明细
	 */
	private List<AfterSaleApplyRequest.AfterSaleItem> items;

	/**
	 * 幂等键
	 */
	private String idempotencyKey;

	/**
	 * 备注
	 */
	private String remark;
}