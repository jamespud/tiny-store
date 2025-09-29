package com.github.spud.tinystore.product.domain.repo;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product p);
    Optional<Product> findById(ProductId id);
}
