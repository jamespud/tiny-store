package com.github.spud.tinystore.order.domain.service.pricing.spi;

/**
 * A pipeline step that mutates the PricingContext in-place.
 */
public interface PricingStep {

	void execute(PricingContext context);
}
