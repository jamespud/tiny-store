package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CheckoutQuoteRequest {

	@NotBlank
	private String userId;

	private String traceId;

	@NotBlank
	private String addressId;

	@NotEmpty
	@Valid
	private List<Line> lines;

	@Valid
	private AppliedIntent appliedIntent;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public String getAddressId() {
		return addressId;
	}

	public void setAddressId(String addressId) {
		this.addressId = addressId;
	}

	public List<Line> getLines() {
		return lines;
	}

	public void setLines(List<Line> lines) {
		this.lines = lines == null ? Collections.emptyList() : lines;
	}

	public AppliedIntent getAppliedIntent() {
		return appliedIntent;
	}

	public void setAppliedIntent(AppliedIntent appliedIntent) {
		this.appliedIntent = appliedIntent;
	}

	public static class Line {
		@NotBlank
		private String skuId;
		@NotBlank
		private String shopId;
		@NotNull
		private Integer quantity;
		@NotNull
		private Long baseUnitPriceCents;
		@NotNull
		private Long weightGrams;

		public String getSkuId() {
			return skuId;
		}

		public void setSkuId(String skuId) {
			this.skuId = skuId;
		}

		public String getShopId() {
			return shopId;
		}

		public void setShopId(String shopId) {
			this.shopId = shopId;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}

		public Long getBaseUnitPriceCents() {
			return baseUnitPriceCents;
		}

		public void setBaseUnitPriceCents(Long baseUnitPriceCents) {
			this.baseUnitPriceCents = baseUnitPriceCents;
		}

		public Long getWeightGrams() {
			return weightGrams;
		}

		public void setWeightGrams(Long weightGrams) {
			this.weightGrams = weightGrams;
		}
	}

	public static class AppliedIntent {
		private List<String> platformCouponIds;
		private Map<String, List<String>> shopCouponIdsByShop;

		public List<String> getPlatformCouponIds() {
			return platformCouponIds;
		}

		public void setPlatformCouponIds(List<String> platformCouponIds) {
			this.platformCouponIds = platformCouponIds == null ? Collections.emptyList() : platformCouponIds;
		}

		public Map<String, List<String>> getShopCouponIdsByShop() {
			return shopCouponIdsByShop;
		}

		public void setShopCouponIdsByShop(Map<String, List<String>> shopCouponIdsByShop) {
			this.shopCouponIdsByShop = shopCouponIdsByShop == null ? Collections.emptyMap() : shopCouponIdsByShop;
		}
	}
}

