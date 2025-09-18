package com.github.spud.tinystore.product.domain.event;

import com.github.spud.tinystore.product.domain.common.DomainEvent;
import com.github.spud.tinystore.product.domain.model.id.ProductId;

import java.time.Instant;
import java.util.UUID;

public record ProductCreated(String id, Instant occurredAt, ProductId productId, String name) implements DomainEvent {
    public ProductCreated(ProductId productId, String name) {
        this(UUID.randomUUID().toString(), Instant.now(), productId, name);
    }
    @Override public String type() { return "product.created"; }
}
