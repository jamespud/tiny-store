package com.github.spud.tinystore.promotion.application.service;

import org.springframework.stereotype.Service;

import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.CheckoutReleaseResponse;
import com.github.spud.tinystore.promotion.interfaces.dto.RollbackRequest;
import com.github.spud.tinystore.promotion.interfaces.dto.RollbackResponse;

@Service
public class RollbackAppService {

	private final CheckoutAppService checkoutAppService;

	public RollbackAppService(CheckoutAppService checkoutAppService) {
		this.checkoutAppService = checkoutAppService;
	}

	public RollbackResponse rollback(String idempotencyKey, RollbackRequest request) {
		if (request.getLockId() == null || request.getLockId().isBlank()) {
			return RollbackResponse.success("released");
		}
		CheckoutReleaseRequest releaseReq = new CheckoutReleaseRequest();
		releaseReq.setQuoteId(request.getLockId());
		releaseReq.setOrderNo(request.getOrderNo());
		releaseReq.setReason(request.getReason());
		CheckoutReleaseResponse releaseResp = checkoutAppService.release(idempotencyKey, releaseReq);
		return releaseResp.isSuccess() ? RollbackResponse.success(releaseResp.getMessage())
			: RollbackResponse.failure(releaseResp.getMessage());
	}
}
