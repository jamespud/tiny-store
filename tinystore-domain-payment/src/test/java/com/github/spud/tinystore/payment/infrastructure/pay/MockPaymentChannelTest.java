package com.github.spud.tinystore.payment.infrastructure.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MockPaymentChannel — channel-ready cashier (A4)")
class MockPaymentChannelTest {

	private final MockPaymentChannel channel = new MockPaymentChannel();

	@Test
	@DisplayName("createChannelPayment returns cashier URL carrying payment order id")
	void createChannelPayment_returnsCashierUrl() {
		Map<String, Object> params = channel.createChannelPayment("DEFAULT", "pay-001", 9900L);
		assertThat(params.get("cashierUrl")).isEqualTo("https://mock-cashier.example.com/pay/pay-001");
		assertThat(params.get("channel")).isEqualTo("DEFAULT");
		assertThat(params.get("amountCents")).isEqualTo(9900L);
	}
}
