package com.github.spud.tinystore.product.application;

import com.github.spud.tinystore.product.domain.model.Sku;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import com.github.spud.tinystore.product.domain.rules.PricingRule;
import com.github.spud.tinystore.product.domain.rules.SimpleRuleEngine;

import java.util.ArrayList;
import java.util.List;

public class PricingService {
    private final List<PricingRule> rules = new ArrayList<>();

    public void registerRule(PricingRule rule) { this.rules.add(rule); }
    public void registerRules(List<PricingRule> rules) { this.rules.addAll(rules); }

    public PricingResult price(Sku sku, PricingContext ctx) {
        var engine = new SimpleRuleEngine().registerAll(rules);
        return engine.evaluate(ctx);
    }
}
