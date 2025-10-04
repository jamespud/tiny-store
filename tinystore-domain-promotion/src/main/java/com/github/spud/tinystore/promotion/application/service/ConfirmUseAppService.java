package com.github.spud.tinystore.promotion.application.service;

import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.ConfirmUseResponse;

public class ConfirmUseAppService {

	public ConfirmUseResponse confirm(String idempotencyKey, ConfirmUseRequest request) {
		// TODO: Validate lock, update user coupon status, adjust budget, emit event.
		return new ConfirmUseResponse();
	}
}
