package com.github.spud.tinystore.product.domain.rules;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.pricing.PricingContext;
import com.github.spud.tinystore.product.domain.pricing.PricingResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class SimpleRuleEngineTest {
    @Test
    void percent_and_flat_rules_apply_with_priority_and_group() {
        var sku = Sku.create(SkuId.of("sku-1"), ProductId.of("p-1"), Money.of(new BigDecimal("100.00")));
        var ctx = new PricingContext(sku, Map.of("brand", "nike"), Instant.now());

        var percent10 = new TimeWindowPercentOffRule("TW10", 100, "promo", Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), new BigDecimal("0.10"));
        var flat20Nike = new TagFlatOffRule("BRAND20", 90, null, "brand", "nike", new BigDecimal("20.00"));

        var engine = new SimpleRuleEngine().register(percent10).register(flat20Nike);
        PricingResult result = engine.evaluate(ctx);

        // 100 - 10% = 90; then -20 = 70
        Assertions.assertThat(result.finalPrice().amount()).isEqualByComparingTo("70.00");
        Assertions.assertThat(result.adjustments()).hasSize(2);
    }
}
