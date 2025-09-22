package com.github.spud.tinystore.order.domain.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.github.spud.tinystore.order.application.command.PreviewOrderCommand.ProductDto;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand.ProductItem;

/**
 * TODO: 远程调用商品服务和优惠券服务
 *
 * @author Spud
 * @date 2025/8/13
 */
@Service
public class ProductValidatorService {

	/**
	 * 检查商品是否有效(未下架)
	 *
	 * @param productIds
	 * @return
	 */
	public boolean validateProducts(List<String> productIds) {
		return true;
	}

	public boolean validateCoupons(Set<String> couponIds) {
		return true;
	}

	public boolean deductProductStock(String productId, Integer quantity) {
		return true;
	}

	public boolean deductProductStocks(List<ProductItem> items) {
		// 提取商品信息：从嵌套的ProductDto中获取skuId和数量
		List<String> skuIds = items.stream()
			.flatMap(item -> item.products().stream())
			.map(ProductDto::skuId)
			.toList();
		List<Integer> quantities = items.stream()
			.flatMap(item -> item.products().stream())
			.map(ProductDto::quantity)
			.toList();
		return deductProductStocks(skuIds, quantities);
	}

	private boolean deductProductStocks(List<String> productIds, List<Integer> quantities) {
		return true;
	}

	public boolean releaseProductStock(String productId, Integer quantity) {
		return true;
	}

	public boolean releaseProductStocks(Map<String, Integer> products) {
		return true;
	}

	public boolean deductCoupons(String userId, Set<String> couponIdAndQuantities) {
		return true;

	}

	public boolean deductCoupon(String userId, String couponId, Integer quantity) {
		return true;
	}
}
