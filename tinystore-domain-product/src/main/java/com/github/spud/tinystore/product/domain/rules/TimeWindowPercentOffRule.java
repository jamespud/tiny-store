package com.github.spud.tinystore.product.domain.rules;

import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import java.math.BigDecimal;
import java.time.Instant;

public record TimeWindowPercentOffRule(
	String code, int priority, String exclusiveGroup,
	Instant start, Instant end, BigDecimal percentOff // 0.10 for 10% off
) implements PricingRule {

	@Override
	public boolean matches(PricingContext ctx) {
		Instant now = ctx.at();
		return (start == null || !now.isBefore(start)) && (end == null || !now.isAfter(end));
	}

	@Override
	public void apply(PricingContext ctx, PricingResult result) {
		var delta = result.basePrice().multiply(percentOff.negate());
		result.apply(new PricingResult.Adjustment(code, "time-window-" + percentOff, delta));
	}
}
