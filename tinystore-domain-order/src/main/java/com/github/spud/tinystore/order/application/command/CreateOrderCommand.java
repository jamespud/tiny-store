package com.github.spud.tinystore.order.application.command;

import com.github.spud.tinystore.order.interfaces.dto.ProductItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class CreateOrderCommand {

	private final String userId;

	private List<ProductItem> products;

	private Set<String> coupons;

	private final String addressId;

	private final String deviceId;

	public List<String> getProductIds() {
		return products.stream().map(ProductItem::skuId).toList();
	}

	public Map<String, Integer> getProductMap() {
		return products.stream()
			.collect(Collectors.toMap(ProductItem::skuId, ProductItem::quantity, Integer::sum));
	}

}
