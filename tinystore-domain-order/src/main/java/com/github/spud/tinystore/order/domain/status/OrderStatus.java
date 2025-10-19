package com.github.spud.tinystore.order.domain.status;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Spud
 * @date 2025/10/18
 */
@Getter
@AllArgsConstructor
public class OrderStatus {
	
	public static final OrderStatus CREATED = new OrderStatus(CoreFlowStatus.PENDING_PAYMENT, PaymentStatus.NONE, FulfillmentStatus.NONE, AfterSaleStatus.NONE);

	/**
	 * 核心流程状态
	 */
	private CoreFlowStatus coreFlowStatus;

	/**
	 * 支付状态
	 */
	private PaymentStatus paymentStatus;

	/**
	 * 履约状态
	 */
	private FulfillmentStatus fulfillmentStatus;

	/**
	 * 售后状态
	 */
	private AfterSaleStatus afterSaleStatus;

}
