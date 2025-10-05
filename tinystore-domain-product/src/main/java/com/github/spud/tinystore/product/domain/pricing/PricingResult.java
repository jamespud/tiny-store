package com.github.spud.tinystore.product.domain.pricing;

import com.github.spud.tinystore.product.domain.model.value.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PricingResult {

	public static class Adjustment {

		public final String ruleCode;
		public final String description;
		public final Money delta; // negative for discount

		public Adjustment(String ruleCode, String description, Money delta) {
			this.ruleCode = ruleCode;
			this.description = description;
			this.delta = delta;
		}
	}

	private final Money basePrice;
	private Money finalPrice;
	private final List<Adjustment> adjustments = new ArrayList<>();

	public PricingResult(Money basePrice) {
		this.basePrice = basePrice;
		this.finalPrice = basePrice;
	}

	public void apply(Adjustment adj) {
		this.finalPrice = this.finalPrice.add(adj.delta);
		this.adjustments.add(adj);
	}

	public Money basePrice() {
		return basePrice;
	}

	public Money finalPrice() {
		return finalPrice;
	}

	public List<Adjustment> adjustments() {
		return Collections.unmodifiableList(adjustments);
	}
}
