package com.github.spud.tinystore.product.domain.repo;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.id.SkuId;

import java.util.Optional;

public interface SkuRepository {
    Sku save(Sku s);
    Optional<Sku> findById(SkuId id);
}
