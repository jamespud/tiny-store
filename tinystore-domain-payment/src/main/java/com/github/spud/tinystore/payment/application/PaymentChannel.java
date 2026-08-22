package com.github.spud.tinystore.payment.application;

import java.util.Map;

/**
 * 支付渠道端口（A4 “真实渠道回调”的代码缝）。
 *
 * <p>负责生成渠道下单所需的收银台/支付参数。默认实现为 {@code MockPaymentChannel}，
 * 接入支付宝/微信时实现本接口并切换 {@code tinystore.payment.channel.type=alipay|wechat} 即可。
 */
public interface PaymentChannel {

	/**
	 * 创建渠道支付参数。
	 *
	 * @param channel 渠道标识（如 DEFAULT / alipay / wechat）
	 * @param paymentOrderId 支付单号
	 * @param amountCents 金额（分）
	 * @return 渠道依赖的收银台参数（至少含 cashierUrl）
	 */
	Map<String, Object> createChannelPayment(String channel, String paymentOrderId, long amountCents);
}
