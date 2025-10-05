package com.github.spud.tinystore.product.domain.event;

import com.github.spud.tinystore.product.domain.common.DomainEvent;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import java.time.Instant;
import java.util.UUID;

public record PriceChangedEvent(String id,
                                Instant occurredAt,
                                ProductId productId,
                                SkuId skuId,
                                Money from,
                                Money to) implements DomainEvent {

	public PriceChangedEvent(ProductId productId, SkuId skuId, Money from, Money to) {
		this(UUID.randomUUID().toString(), Instant.now(), productId, skuId, from, to);
	}

	@Override
	public String type() {
		return "price.changed";
	}
}
