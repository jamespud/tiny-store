package com.github.spud.tinystore.product.domain.repository;

import java.util.List;
import java.util.Optional;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;

public interface SkuRepository {
    Sku save(Sku sku);

    Optional<Sku> findById(SkuId skuId);

    List<Sku> findByProductId(ProductId productId);

    void delete(SkuId skuId);
}
