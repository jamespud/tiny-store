package com.github.spud.tinystore.order.application.command;

import com.github.spud.tinystore.order.application.command.PreviewOrderCommand.ProductItem;
import com.github.spud.tinystore.order.domain.model.Order;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Builder
public class CreateOrderCommand {

	private final String userId;

	private List<PreviewOrderCommand.ProductItem> products;

	private Set<String> coupons;

	private final String addressId;

	private final String deviceId;
	
	private final String idempotentKey;

	public List<String> getProductIds() {
		return List.of();
	}

	public Map<String, Integer> getProductMap() {
		return Map.of();
	}
	
	public String getUserId() {
		return userId;
	}

	public List<ProductItem> getProducts() {
		return products;
	}

	public void setProducts(
		List<ProductItem> products) {
		this.products = products;
	}

	public Set<String> getCoupons() {
		return coupons;
	}

	public void setCoupons(Set<String> coupons) {
		this.coupons = coupons;
	}

	public String getAddressId() {
		return addressId;
	}

	public String getDeviceId() {
		return deviceId;
	}
}
