package com.github.spud.tinystore.inventory.domain.model;

import java.util.Objects;

/**
 * 值对象：店铺+SKU 标识
 */
public final class ShopSkuId {

	private final String shopId;
	private final String skuId;

	public ShopSkuId(String shopId, String skuId) {
		if (shopId == null || skuId == null || shopId.isBlank() || skuId.isBlank()) {
			throw new IllegalArgumentException("tenantId/skuId 不能为空");
		}
		if (shopId.length() > 64 || skuId.length() > 64) {
			throw new IllegalArgumentException("tenantId/skuId 长度超限");
		}
		this.shopId = shopId;
		this.skuId = skuId;
	}

	public String shopId() {
		return shopId;
	}

	public String skuId() {
		return skuId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ShopSkuId that)) {
			return false;
		}
		return shopId.equals(that.shopId) && skuId.equals(that.skuId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shopId, skuId);
	}

	@Override
	public String toString() {
		return shopId + ":" + skuId;
	}
}

