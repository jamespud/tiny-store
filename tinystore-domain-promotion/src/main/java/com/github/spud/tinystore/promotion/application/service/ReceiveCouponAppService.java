package com.github.spud.tinystore.promotion.application.service;

import com.github.spud.tinystore.promotion.interfaces.dto.ReceiveCouponRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.ReceiveCouponResponse;

public class ReceiveCouponAppService {

	public ReceiveCouponResponse receiveCoupon(String idempotencyKey, ReceiveCouponRequest request) {
		// TODO: 10-step orchestration per plan
		// 1. Validate request & frequency limit
		// 2. Check user eligibility & repeated claim
		// 3. Execute Lua to decrement stock
		// 4. Persist user coupon record
		// 5. Publish COUPON_RECEIVED event
		// 6. Return remaining stock info
		return new ReceiveCouponResponse();
	}
}
