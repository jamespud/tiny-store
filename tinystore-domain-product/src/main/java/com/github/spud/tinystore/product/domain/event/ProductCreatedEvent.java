package com.github.spud.tinystore.product.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.github.spud.tinystore.product.domain.common.DomainEvent;
import com.github.spud.tinystore.product.domain.model.id.ProductId;

public record ProductCreatedEvent(String id,
                                  Instant occurredAt,
                                  ProductId productId,
                                  String productName) implements DomainEvent {

    public ProductCreatedEvent(ProductId productId, String productName) {
        this(UUID.randomUUID().toString(), Instant.now(), productId, productName);
    }

    @Override
    public String type() {
        return "product.created";
    }
}