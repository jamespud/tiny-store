package com.github.spud.tinystore.order.application.command;

import com.github.spud.tinystore.order.interfaces.dto.ProductItem;
import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class PreviewOrderCommand {

	String userId;

	private List<ProductItem> products;

	private Set<String> coupons;

	private String addressId;

	private String deviceId;

	public List<String> getProductIds() {
		return products.stream().map(ProductItem::skuId).toList();
	}

}
