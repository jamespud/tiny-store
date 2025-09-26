package com.github.spud.tinystore.order.domain.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
public class UserValidatorService {

	public boolean canUserPlaceOrder(String userId, List<String> skuId) {
		return true;
	}

	public boolean validateAddress(String userId, String addressId) {
		return true;
	}

}
