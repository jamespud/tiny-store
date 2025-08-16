package com.github.spud.tinystore.order.domain.client;

import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import jakarta.ws.rs.Consumes;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/8/16
 */
@FeignClient("tinystore-payment")
public interface PaymentDomainService {


	@Consumes("application/json")
	PaymentIntent createPaymentIntent(long amount, String description, String orderId);
}
