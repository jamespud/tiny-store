package com.github.spud.tinystore.order.infrastructure.gateway;

import java.util.UUID;

/**
 * 支付网关服务占位接口（当前不实现真实支付逻辑） 后续可扩展： - voidPayment 撤销未结算支付 - refundPayment 发起退款
 */
public interface PaymentGatewayService {

	/**
	 * 撤销支付（占位：当前实现层可直接返回成功）
	 *
	 * @param paymentIntentId 支付意图ID
	 * @return 撤销结果
	 */
	PaymentVoidResult voidPayment(UUID paymentIntentId);
}
