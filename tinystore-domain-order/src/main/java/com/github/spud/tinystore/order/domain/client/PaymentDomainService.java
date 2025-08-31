package com.github.spud.tinystore.order.domain.client;

import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import jakarta.ws.rs.Consumes;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/16
 */
@FeignClient("tinystore-payment")
public interface PaymentDomainService {


	@Consumes("application/json")
	PaymentIntent createPaymentIntent(long amount, String description, String orderId);

	@Consumes("application/json")
	String refundPayment(String paymentIntentId, long amount, String reason, Instant requestTime);
}
