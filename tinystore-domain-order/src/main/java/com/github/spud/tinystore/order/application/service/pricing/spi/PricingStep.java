package com.github.spud.tinystore.order.application.service.pricing.spi;

/**
 * A pipeline step that mutates the PricingContext in-place.
 */
public interface PricingStep {

	void execute(PricingContext context);
}
