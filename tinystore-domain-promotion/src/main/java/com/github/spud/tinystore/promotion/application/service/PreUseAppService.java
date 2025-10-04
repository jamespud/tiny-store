package com.github.spud.tinystore.promotion.application.service;

import com.github.spud.tinystore.promotion.interfaces.dto.PreUseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.PreUseResponse;

public class PreUseAppService {

	public PreUseResponse preUse(String idempotencyKey, PreUseRequest request) {
		// TODO: Orchestrate rule evaluation, lock creation, and response mapping.
		return new PreUseResponse();
	}
}
