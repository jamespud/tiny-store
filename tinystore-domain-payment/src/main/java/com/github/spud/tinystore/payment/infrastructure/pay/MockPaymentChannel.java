package com.github.spud.tinystore.payment.infrastructure.pay;

import com.github.spud.tinystore.payment.application.PaymentChannel;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认 mock 渠道：返回模拟收银台链接，保持现有行为。接入真实渠道时替换。
 */
@Component
@ConditionalOnProperty(prefix = "tinystore.payment.channel", name = "type",
	havingValue = "mock", matchIfMissing = true)
public class MockPaymentChannel implements PaymentChannel {

	@Override
	public Map<String, Object> createChannelPayment(String channel, String paymentOrderId, long amountCents) {
		Map<String, Object> result = new HashMap<>();
		result.put("channel", channel != null ? channel : "DEFAULT");
		result.put("amountCents", amountCents);
		result.put("cashierUrl", "https://mock-cashier.example.com/pay/" + paymentOrderId);
		return result;
	}
}
