package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;
import com.github.spud.tinystore.order.interfaces.dto.ProductDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Spud
 * @date 2025/9/5
 */
@Service
public class ProductService {

	private ProductClient productClient;

	public List<Product> getProductsByIds(List<String> productIds) {
		productClient.getProductsByIds(productIds);
		return List.of();
	}

	public List<Product> getCouponsByIds(String userId, List<String> couponIds) {
		return List.of();
	}

	public boolean validateProducts(List<String> productIds) {
		return true;
	}

	public boolean validateCoupons(Set<String> couponIds) {
		return true;
	}

	public boolean deductProductStock(String productId, Integer quantity) {
		return true;
	}

	public boolean deductProductStocks(List<PreviewOrderCommand.ProductItem> items) {
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