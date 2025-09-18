package com.github.spud.tinystore.product.domain.event;

import com.github.spud.tinystore.product.domain.common.DomainEvent;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.id.SkuId;
import com.github.spud.tinystore.product.domain.model.value.Money;

import java.time.Instant;
import java.util.UUID;

public record PriceChanged(String id, Instant occurredAt, ProductId productId, SkuId skuId, Money from, Money to) implements DomainEvent {
    public PriceChanged(ProductId productId, SkuId skuId, Money from, Money to) {
        this(UUID.randomUUID().toString(), Instant.now(), productId, skuId, from, to);
    }
    @Override public String type() { return "price.changed"; }
}
