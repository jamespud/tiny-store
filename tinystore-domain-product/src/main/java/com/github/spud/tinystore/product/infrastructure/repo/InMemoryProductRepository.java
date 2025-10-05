package com.github.spud.tinystore.product.infrastructure.repo;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProductRepository implements ProductRepository {

	private final Map<String, Product> store = new ConcurrentHashMap<>();

	@Override
	public Product save(Product product) {
		store.put(product.getProductId().getId(), product);
		return product;
	}

	@Override
	public Optional<Product> findById(ProductId productId) {
		return Optional.ofNullable(store.get(productId.getId()));
	}

	@Override
	public void delete(ProductId productId) {
		store.remove(productId.getId());
	}
}
