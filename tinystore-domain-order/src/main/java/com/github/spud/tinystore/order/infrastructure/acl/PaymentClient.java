package com.github.spud.tinystore.order.infrastructure.acl;

import jakarta.ws.rs.Consumes;
import org.springframework.cloud.openfeign.FeignClient;

import java.time.Instant;

/**
 * @author Spud
 * @date 2025/8/16
 */
@FeignClient("tinystore-payment")
public interface PaymentClient {

	@Consumes("application/json")
	String refundPayment(String paymentIntentId, long amount, String reason, Instant requestTime);
}
