package com.github.spud.tinystore.order.application.service.pricing.step;

import com.github.spud.tinystore.order.application.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingStep;
import org.springframework.stereotype.Component;

@Component
public class TaxStep implements PricingStep {

    @Override
    public void execute(PricingContext context) {
        // Placeholder: no tax for now
        context.setTaxTotal(0L);
    }
}
