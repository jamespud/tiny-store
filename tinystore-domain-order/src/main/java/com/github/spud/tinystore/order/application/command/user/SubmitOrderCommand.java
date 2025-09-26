package com.github.spud.tinystore.order.application.command.user;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Getter
@Builder
public class SubmitOrderCommand {

	private final String userId;

	private List<PreviewOrderCommand.ProductItem> productItems;

	private Set<String> couponsIds;

	private final String addressId;

	private final String deviceId;

	private final String idempotentKey;

	public List<String> getSkuIds() {
		return productItems.stream()
			.map(PreviewOrderCommand.ProductItem::skuId)
			.toList();
	}


}
