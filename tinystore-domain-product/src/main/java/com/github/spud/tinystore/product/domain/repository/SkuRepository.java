package com.github.spud.tinystore.product.domain.repository;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import java.util.List;
import java.util.Optional;

public interface SkuRepository {

	Sku save(Sku sku);

	Optional<Sku> findById(SkuId skuId);

	List<Sku> findByProductId(ProductId productId);

	void delete(SkuId skuId);
}
