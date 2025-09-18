package com.github.spud.tinystore.product.domain.model.id;

import java.util.Objects;

public record SkuId(String value) {
    public SkuId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("skuId blank");
    }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) { return o instanceof SkuId sid && Objects.equals(sid.value, value); }
    @Override public int hashCode() { return Objects.hash(value); }
}
