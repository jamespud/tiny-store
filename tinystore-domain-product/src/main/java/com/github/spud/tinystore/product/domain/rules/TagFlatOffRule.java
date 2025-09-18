package com.github.spud.tinystore.product.domain.rules;

import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;

import java.math.BigDecimal;

public record TagFlatOffRule(
        String code, int priority, String exclusiveGroup,
        String tagKey, String tagValue, BigDecimal flatOff // e.g., 20.00 off
) implements PricingRule {
    @Override public boolean matches(PricingContext ctx) {
        Object v = ctx.attributes().get(tagKey);
        return v != null && v.toString().equals(tagValue);
    }

    @Override public void apply(PricingContext ctx, PricingResult result) {
        var delta = Money.of(flatOff.negate());
        result.apply(new PricingResult.Adjustment(code, "tag-"+tagKey+"="+tagValue, delta));
    }
}
