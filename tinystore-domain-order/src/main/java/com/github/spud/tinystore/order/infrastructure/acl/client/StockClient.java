package com.github.spud.tinystore.order.infrastructure.acl.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author Spud
 * @date 2025/9/3
 */
@FeignClient("tinystore-stock")
public interface StockClient {

	@GetMapping(value = "/internal/restful/stock/reserve", consumes = "application/json")
	boolean reserveStock(String productId, int quantity);

	@PostMapping(value = "/internal/restful/stock/release", consumes = "application/json")
	boolean releaseStock(String productId, int quantity);

	@GetMapping(value = "/internal/restful/stock/check", consumes = "application/json")
	boolean checkStock(String productId, int quantity);

	@GetMapping(value = "/internal/restful/stock/check/batch", consumes = "application/json")
	boolean checkStockBatch(String[] productIds, int[] quantities);

}
