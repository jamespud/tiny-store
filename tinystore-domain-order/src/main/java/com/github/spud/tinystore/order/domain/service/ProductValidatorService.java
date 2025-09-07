package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.interfaces.dto.ProductItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
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
		List<String> skuIds = items.stream().map(ProductItem::skuId).toList();
		List<Integer> quantities = items.stream().map(ProductItem::quantity).toList();
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
