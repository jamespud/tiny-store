package com.github.spud.tinystore.product.domain.repository;

import java.util.List;
import java.util.Optional;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.id.SkuId;

public interface SkuRepository {
    Sku save(Sku sku);

    Optional<Sku> findById(SkuId skuId);

    List<Sku> findByProductId(ProductId productId);

    void delete(SkuId skuId);
}
