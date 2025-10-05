package com.github.spud.tinystore.product.domain.repository;

import java.util.Optional;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;ystore.product.domain.model.id.ProductId;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(ProductId productId);

    void delete(ProductId productId);
}