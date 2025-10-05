package com.github.spud.tinystore.product.application.factory;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.model.valueobject.SpecificationCombination;

public final class SkuAggregateFactory {

	private SkuAggregateFactory() {
	}

	public static Sku createWithBasePrice(SkuId skuId, ProductId productId, Money basePrice) {
		SpecificationCombination combination = SpecificationCombination.single("default", "default");
		return Sku.create(skuId.getId(), productId.getId(), combination, null);
	}
}

