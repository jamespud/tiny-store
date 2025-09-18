package com.github.spud.tinystore.product.domain.repository;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(ProductId productId);

    void delete(ProductId productId);
}