package com.github.spud.tinystore.inventory.domain.service;

import com.github.spud.tinystore.infrastructure.service.RedisOperator;
import com.github.spud.tinystore.inventory.domain.constant.RedisConstant;
import com.github.spud.tinystore.inventory.domain.model.StockAggregate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 缓存层库存操作（含计划新增查询占位） Refactored: 迁移方法命名 increaseReserved/decreaseReserved ->
 * increaseReserve/decreaseReserve
 */
@Slf4j
@Service
public class CacheStockService {

	@Autowired
	private RedisOperator redisOperator;

	public boolean increaseTotal(String shopId, String skuId, Integer quantity, Integer dbVersion) {
		// 参数校验
		if (shopId == null || skuId == null || quantity == null || quantity <= 0 || dbVersion == null) {
			log.error("Invalid parameters");
			return false;
		}

		int maxRetries = 3;
		long sleepMillis = 50;
		for (int retry = 0; retry < maxRetries; retry++) {
			if (redisOperator.totalStockIncr(
				RedisConstant.STOCK_TOTAL_KEY_PREFIX + shopId + ":" + skuId,
				quantity,
				RedisConstant.STOCK_TOTAL_VERSION_KEY_PREFIX + shopId + ":" + skuId,
				dbVersion)) {
				return true;
			}
			try {
				Thread.sleep(sleepMillis);
				sleepMillis = Math.min(sleepMillis * 2, 1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Operation interrupted", e);
				return false;
			}
		}
		log.info("Increase stock failed after retries for tenantId: {}, skuId: {}", shopId, skuId);
		return false;
	}

	public boolean decreaseTotal(String shopId, String skuId, Integer quantity, Integer dbVersion) {
		// 参数校验
		if (shopId == null || skuId == null || quantity == null || quantity <= 0 || dbVersion == null) {
			log.error("Invalid parameters");
			return false;
		}

		int maxRetries = 3;
		long sleepMillis = 50;
		for (int retry = 0; retry < maxRetries; retry++) {
			if (redisOperator.totalStockDecr(
				RedisConstant.STOCK_TOTAL_KEY_PREFIX + shopId + ":" + skuId,
				quantity,
				RedisConstant.STOCK_TOTAL_VERSION_KEY_PREFIX + shopId + ":" + skuId,
				dbVersion)) {
				return true;
			}
			try {
				Thread.sleep(sleepMillis);
				sleepMillis = Math.min(sleepMillis * 2, 1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Operation interrupted", e);
				return false;
			}
		}
		log.info("Decrease stock failed after retries for tenantId: {}, skuId: {}", shopId, skuId);
		return false;
	}

	public boolean decreaseReserve(String shopId, String skuId, Integer quantity) {
		// 参数校验
		if (shopId == null || skuId == null || quantity == null || quantity <= 0) {
			log.error("Invalid parameters");
			return false;
		}

		if (redisOperator.batchStockDecr(
			List.of(RedisConstant.STOCK_RESERVED_KEY_PREFIX + shopId + ":" + skuId),
			List.of(quantity))) {
			return true;
		}
		log.info("Decrease reserved stock failed for tenantId: {}, skuId: {}", shopId, skuId);
		return false;
	}

	public boolean increaseReserve(String shopId, String skuId, Integer quantity) {
		// 参数校验
		if (shopId == null || skuId == null || quantity == null || quantity <= 0) {
			log.error("Invalid parameters");
			return false;
		}

		if (redisOperator.batchStockIncr(
			List.of(RedisConstant.STOCK_RESERVED_KEY_PREFIX + shopId + ":" + skuId),
			List.of(quantity))) {
			return true;
		}
		log.info("Increase reserved stock failed for tenantId: {}, skuId: {}", shopId, skuId);
		return false;
	}

	/**
	 * @deprecated 使用 {@link #increaseReserve(String, String, Integer)}
	 */
	@Deprecated(forRemoval = true)
	public boolean increaseReserved(String shopId, String skuId, Integer quantity) {
		return increaseReserve(shopId, skuId, quantity);
	}

	/**
	 * @deprecated 使用 {@link #decreaseReserve(String, String, Integer)}
	 */
	@Deprecated(forRemoval = true)
	public boolean decreaseReserved(String shopId, String skuId, Integer quantity) {
		return decreaseReserve(shopId, skuId, quantity);
	}

	// === 新增占位方法 ===
	public Optional<StockAggregate> getSnapshot(String shopId, String skuId) {
		// TODO(inv): 从 Redis 读取 total/reserved/version 组装 Stock
		return Optional.empty();
	}

	public int getAvailable(String shopId, String skuId) {
		// TODO(inv): 计算 total - reserved（缓存缺失时回退 0 或触发 sync）
		return 0;
	}
}
