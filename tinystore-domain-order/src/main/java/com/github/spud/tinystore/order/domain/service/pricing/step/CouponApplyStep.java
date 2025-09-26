package com.github.spud.tinystore.order.domain.service.pricing.step;

import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingStep;
import org.springframework.stereotype.Component;

@Component
public class CouponApplyStep implements PricingStep {

	@Override
	public void execute(PricingContext context) {
		// Placeholder: no coupon amount for now (keep 0)
		// If future coupons apply, add an ORDER-scoped negative adjustment and roll into amount.
	}
}
