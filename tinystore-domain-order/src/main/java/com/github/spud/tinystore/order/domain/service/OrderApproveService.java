package com.github.spud.tinystore.order.domain.service;

import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/30
 */
@Service
public class OrderApproveService {

	/**
	 * 发送商家退款消息
	 *
	 * @param orderId
	 */
	public void sendApproveMessage(String orderId) {
		// 保存到消息表
		// 发送消息

	}

	/**
	 * 商家同意退款
	 *
	 * @param orderId
	 */
	public void approveRefund(String orderId) {

	}
}
