package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutReleaseRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutReleaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/5
 */
@Service
public class PromotionService {

	@Autowired
	private PromotionClient promotionClient;

	public CheckoutQuoteResponse checkoutQuote(String idempotencyKey, CheckoutQuoteRequest request) {
		return promotionClient.checkoutQuote(idempotencyKey, request);
	}

	public CheckoutCommitResponse checkoutCommit(String idempotencyKey, CheckoutCommitRequest request) {
		return promotionClient.checkoutCommit(idempotencyKey, request);
	}

	public CheckoutReleaseResponse checkoutRelease(String idempotencyKey, CheckoutReleaseRequest request) {
		return promotionClient.checkoutRelease(idempotencyKey, request);
	}
}
