package com.github.spud.tinystore.product.domain.pricing;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import java.time.Instant;
import java.util.Map;

public record PricingContext(Sku sku, Map<String, Object> attributes, Instant at) {

	public PricingContext {
		if (at == null) {
			at = Instant.now();
		}
	}

	public static PricingContext of(Sku sku) {
		return new PricingContext(sku, Map.of(), Instant.now());
	}
}
