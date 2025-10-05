package com.github.spud.tinystore.product.domain.rules;

import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;

public interface PricingRule extends Comparable<PricingRule> {

	String code();

	int priority(); // higher executes earlier

	String exclusiveGroup(); // null or group name; when applied, other rules with same group are skipped

	boolean matches(PricingContext ctx);

	void apply(PricingContext ctx, PricingResult result);

	@Override
	default int compareTo(PricingRule o) {
		return Integer.compare(o.priority(), this.priority());
	}
}
