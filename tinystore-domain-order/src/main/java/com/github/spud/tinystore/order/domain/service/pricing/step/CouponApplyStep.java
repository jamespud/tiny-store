package com.github.spud.tinystore.order.domain.service.pricing.step;

import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingStep;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import org.springframework.stereotype.Component;

@Component
public class CouponApplyStep implements PricingStep {

	@SuppressWarnings("unused")
	private final PromotionClient promotionClient;

	public CouponApplyStep(PromotionClient promotionClient) {
		this.promotionClient = promotionClient;
	}

	@Override
	public void execute(PricingContext context) {
		// TODO: Invoke promotionClient.preUse(...) with idempotency key and convert response into pricing adjustments.
	}
}
