package com.github.spud.tinystore.product.domain.repository;

import com.github.spud.tinystore.product.domain.model.Stock;
import java.util.Optional;

/**
 * 库存仓储接口（仅方法签名占位）
 */
public interface StockRepository {
	Optional<Stock> findByShopSku(String shopId, String skuId);
	boolean insertIfAbsent(Stock stock);
	boolean updateForAdjust(String shopId, String skuId, int deltaTotal, int expectedVersion);
	boolean updateReserved(String shopId, String skuId, int deltaReserved, int expectedVersion);
	boolean updateForReservationConfirm(String shopId, String skuId, int deltaReserved, int deltaTotal, int expectedVersion);
}

