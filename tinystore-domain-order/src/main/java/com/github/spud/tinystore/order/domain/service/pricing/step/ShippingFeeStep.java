package com.github.spud.tinystore.order.domain.service.pricing.step;

import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingStep;
import org.springframework.stereotype.Component;

@Component
public class ShippingFeeStep implements PricingStep {

	@Override
	public void execute(PricingContext context) {
		// Placeholder fixed shipping fee in cents; make configurable later
		long shipping = 1200L; // 12.00 CNY in cents
		context.setShippingFee(shipping);
	}
}
