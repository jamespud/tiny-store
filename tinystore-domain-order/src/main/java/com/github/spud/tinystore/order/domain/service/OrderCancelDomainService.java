package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import com.github.spud.tinystore.order.constant.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.CancelDecisionType;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 订单取消领域服务
 *
 * @author Spud
 * @date 2025/8/28
 */
@Service
public class OrderCancelDomainService {

	/**
	 * 判断订单取消决策
	 *
	 * @param order 订单
	 * @param now   当前时间
	 * @return 取消决策类型
	 */
	public CancelDecisionType decide(Order order, Instant now) {
		// 订单不存在的情况
		if (order == null) {
			return CancelDecisionType.NOT_ALLOW;
		}

		// 获取订单状态
		String status = getOrderStatus(order);
		if (status == null) {
			return CancelDecisionType.NOT_ALLOW;
		}
		// 简单取消集合
		if (statusEquals(status, OrderStatus.CREATED, OrderStatus.PAYMENT_PROCESSING, OrderStatus.PAID,
			OrderStatus.ACCEPT_PENDING)) {
			return CancelDecisionType.ALLOW_SIMPLE;
		}
		// 需商家审批集合
		if (statusEquals(status, OrderStatus.ACCEPTED, OrderStatus.PACKING, OrderStatus.SHIP_PENDING)) {
			return CancelDecisionType.ALLOW_WITH_MERCHANT_APPROVAL;
		}
		// 发货及终态集合 → 不允许
		return CancelDecisionType.NOT_ALLOW;
	}

	/**
	 * 获取订单状态
	 *
	 * @param order 订单
	 * @return 订单状态
	 */
	private String getOrderStatus(Order order) {
		// TODO: 实际项目中需要从order对象中获取状态
		return order.getOrderStatus();
	}

	private boolean statusEquals(String code, OrderStatus... statuses) {
		for (OrderStatus s : statuses) {
			if (s.getCode().equals(code)) {
				return true;
			}
		}
		return false;
	}

}
