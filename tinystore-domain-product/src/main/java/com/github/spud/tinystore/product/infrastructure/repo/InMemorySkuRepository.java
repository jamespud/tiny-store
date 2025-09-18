package com.github.spud.tinystore.product.infrastructure.repo;

import com.github.spud.tinystore.product.domain.model.Sku;
import com.github.spud.tinystore.product.domain.model.id.SkuId;
import com.github.spud.tinystore.product.domain.repo.SkuRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySkuRepository implements SkuRepository {
    private final Map<String, Sku> store = new ConcurrentHashMap<>();
    @Override public Sku save(Sku s) { store.put(s.getId().value(), s); return s; }
    @Override public Optional<Sku> findById(SkuId id) { return Optional.ofNullable(store.get(id.value())); }
}
