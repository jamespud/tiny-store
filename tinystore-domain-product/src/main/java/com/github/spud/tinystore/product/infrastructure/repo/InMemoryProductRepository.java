package com.github.spud.tinystore.product.infrastructure.repo;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.repo.ProductRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> store = new ConcurrentHashMap<>();
    @Override public Product save(Product p) { store.put(p.getId().value(), p); return p; }
    @Override public Optional<Product> findById(ProductId id) { return Optional.ofNullable(store.get(id.value())); }
}
