package com.github.spud.tinystore.product.domain.rules;

import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;

import java.util.*;

public class SimpleRuleEngine {
    private final List<PricingRule> rules = new ArrayList<>();

    public SimpleRuleEngine register(PricingRule rule) { rules.add(rule); return this; }
    public SimpleRuleEngine registerAll(Collection<PricingRule> rs) { rules.addAll(rs); return this; }

    public PricingResult evaluate(PricingContext ctx) {
        var result = new PricingResult(ctx.sku().getBasePrice());
        var appliedGroups = new HashSet<String>();
        rules.stream().sorted().forEach(rule -> {
            String group = rule.exclusiveGroup();
            if (group != null && appliedGroups.contains(group)) return;
            if (rule.matches(ctx)) {
                rule.apply(ctx, result);
                if (group != null) appliedGroups.add(group);
            }
        });
        return result;
    }
}
