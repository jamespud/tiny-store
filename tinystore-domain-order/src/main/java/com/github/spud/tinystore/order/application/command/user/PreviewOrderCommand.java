package com.github.spud.tinystore.order.application.command.user;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Builder
@Getter
public class PreviewOrderCommand {

	String userId;

	private List<ProductItem> productItems;

	private Set<String> couponIds;

	private String addressId;

	private String deviceId;

	public List<String> getSkuIds() {
		return productItems.stream()
			.map(ProductItem::skuId)
			.toList();
	}

	public record ProductItem(String shopId, String spuId, String skuId, Integer quantity) {

	}
}
