package com.github.spud.tinystore.order.application.service.pricing.step;

import com.github.spud.tinystore.order.application.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingStep;
import org.springframework.stereotype.Component;

@Component
public class SummaryStep implements PricingStep {

    @Override
    public void execute(PricingContext context) {
        long payable = context.getItemsTotal() + context.getShippingFee() + context.getTaxTotal()
                - context.getDiscountTotal();
        context.setPayable(payable);
        context.setGrandTotal(payable); // same in current model
    }
}
