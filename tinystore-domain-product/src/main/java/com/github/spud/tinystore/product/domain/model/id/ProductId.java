package com.github.spud.tinystore.product.domain.model.id;

import java.util.Objects;

public record ProductId(String value) {
    public ProductId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("productId blank");
    }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) { return o instanceof ProductId pid && Objects.equals(pid.value, value); }
    @Override public int hashCode() { return Objects.hash(value); }
}
