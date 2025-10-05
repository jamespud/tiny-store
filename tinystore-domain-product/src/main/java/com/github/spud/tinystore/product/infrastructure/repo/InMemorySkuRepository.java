package com.github.spud.tinystore.product.infrastructure.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.id.SkuId;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;

public class InMemorySkuRepository implements SkuRepository {
    private final Map<String, Sku> store = new ConcurrentHashMap<>();

    @Override
    public Sku save(Sku sku) {
        store.put(sku.getSkuId(), sku);
        return sku;
    }

    @Override
    public Optional<Sku> findById(SkuId skuId) {
        return Optional.ofNullable(store.get(skuId.value()));
    }

    @Override
    public List<Sku> findByProductId(ProductId productId) {
        return store.values().stream()
                .filter(sku -> sku.getProductId().equals(productId.value()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(SkuId skuId) {
        store.remove(skuId.value());
    }
}
