package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/13
 */
@Service
public class OrderService {

	@Autowired
	private OrderRepository orderRepository;

	/**
	 * 简单取消订单 不需要商家同意，直接取消。执行退款、释放库存等操作
	 *
	 * @param order
	 */
	public void processCancelSimple(Order order) {
		// TODO: 
		String orderStatus = order.getOrderStatus();
		// 需要退款
		if ("PAID".equals(orderStatus) || "FULFILLING".equals(orderStatus)) {
			// 执行退款逻辑
		}
		// 释放库存
		// 更新订单状态

	}

	public void processCancelWithMerchantApproval(Order order) {

	}

}
