package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.status.PaymentStatus;
import java.util.List;

/**
 * 拆分后的子订单
 * 
 * @param subOrderId 子订单ID
 * @param shopId     店铺ID
 * @param lines      订单行
 * @param charges    额外费用（如运费、税费等）
 * @param discounts  优惠分摊
 * @param subtotal   小计
 * @param payable    应付总额
 * @author Spud
 * @date 2025/9/6
 */
public record SubOrder(String subOrderId, String shopId, List<OrderLine> lines,
                       List<ChargeItem> charges, List<DiscountAllocation> discounts,
                       Money subtotal, Money payable, FulfillmentStatus fulfillmentStatus,
                       OrderStatus orderStatus, PaymentStatus paymentStatus,
                       AfterSaleStatus afterSaleStatus,
                       Address address) {

	long computeSubtotal() {
		// TODO: 计算小计
		return 0;
	}

	long recompute() {
		return 0;
	}

	void applyDiscountAllocations() {
	}

	void markShipped(List<Integer> partialLines) {
	}

	void markDelivered() {
	}

	boolean isFulfilled() {
		// TODO:
		return false;
	}
}