package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public class IdempotencyStorage {

	public boolean exists(String idempotencyKey) {
		return false;
	}

	public CreateOrderResponse getResponse(String idempotencyKey,
		Class<CreateOrderResponse> createOrderResponseClass) {
		return null;
	}

	public void save(String idempotencyKey, CreateOrderResponse response, int i) {
		
	}
}
