package com.github.spud.tinystore.promotion.application.service;

import com.github.spud.tinystore.promotion.interfaces.dto.RollbackRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.RollbackResponse;

public class RollbackAppService {

	public RollbackResponse rollback(String idempotencyKey, RollbackRequest request) {
		// TODO: Handle FULL/PARTIAL refund logic, inventory adjustments, and event emission.
		return RollbackResponse.success("pending");
	}
}
