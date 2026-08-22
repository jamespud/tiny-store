package com.github.spud.tinystore.infrastructure.rpc.payment;

import com.github.spud.tinystore.infrastructure.rpc.dto.ProductSkuPriceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品域 Feign 客户端。
 *
 * <p>用于在订购时获取服务端权威 SKU 价格（防客户端自定价格）。只保留一个
 * name = product-service 的客户端，避免与 order.infra.acl 扫描路径重复注册。
 * 订单域通过 {@code @EnableFeignClients} 扫描本包注册。
 */
@FeignClient(name = "product-service")
public interface ProductClient {

	/**
	 * 批量获取指定店铺下 SKU 的权威价格（商品域内部端点）。
	 * skuIdsCsv 为逗号分隔的 skuId 列表。
	 */
	@GetMapping("/internal/restful/sku/batch")
	ProductSkuPriceResponse batchSkuPrices(
		@RequestParam("shopId") String shopId,
		@RequestParam("skuIds") String skuIdsCsv);
}
