package com.github.spud.tinystore.order.application.command;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Builder
@Getter
public class PreviewOrderCommand {

	String userId;

	private List<ProductItem> products;

	private Set<String> coupons;

	private String addressId;

	private String deviceId;

	public List<String> getProductIds() {
		return products.stream()
			.flatMap(p -> p.products.stream().map(ProductDto::skuId))
			.toList();
	}

	public record ProductItem(String shopId, List<ProductDto> products) {

	}

	public record ProductDto(String spuId, String skuId, Integer quantity) {
		
	}

}
