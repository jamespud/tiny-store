package com.github.spud.tinystore.order.infrastructure.acl;

import jakarta.ws.rs.Consumes;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/16
 */
@FeignClient("tinystore-payment")
public interface PaymentClient {


	@Consumes("application/json")
	PaymentIntent createPaymentIntent(long amount, String description, String orderId);

	@Consumes("application/json")
	String refundPayment(String paymentIntentId, long amount, String reason, Instant requestTime);
}
