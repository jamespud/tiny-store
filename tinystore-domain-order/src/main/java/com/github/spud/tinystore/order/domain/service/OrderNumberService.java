package com.github.spud.tinystore.order.domain.service;

import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/8
 */
@Service
public class OrderNumberService {

	public String generateOrderNumber(String userId) {
		// Implement a simple order number generation logic
		// In a real-world scenario, this could be more complex and ensure uniqueness
		long timestamp = System.currentTimeMillis();
		int randomSuffix = (int) (Math.random() * 10000);
		return String.format("ORD-%s-%d-%04d", userId, timestamp, randomSuffix);
	}
}
