package com.github.spud.tinystore.inventory.domain.repository;

import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import java.util.List;
import java.util.Optional;

/**
 * 库存聚合仓储接口
 */
public interface StockRepository {

	Optional<StockAggregate> find(String shopId, String skuId);

	void insert(StockAggregate aggregate);

	boolean update(StockAggregate aggregate); // 乐观锁，true 表示成功

	List<StockAggregate> batchFind(List<ShopSkuKey> keys);

	record ShopSkuKey(String shopId, String skuId) {

	}
}

