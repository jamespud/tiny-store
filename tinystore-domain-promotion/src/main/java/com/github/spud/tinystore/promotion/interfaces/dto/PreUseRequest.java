package com.github.spud.tinystore.promotion.interfaces.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PreUseRequest {

	@NotBlank
	private String userId;

	private List<String> couponIds;

	@NotNull
	private List<SkuDetail> skus;

	@NotNull
	private BigDecimal orderAmount;

	private String userTags;

	private String traceId;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<String> getCouponIds() {
		return couponIds;
	}

	public void setCouponIds(List<String> couponIds) {
		this.couponIds = couponIds;
	}

	public List<SkuDetail> getSkus() {
		return skus;
	}

	public void setSkus(List<SkuDetail> skus) {
		this.skus = skus;
	}

	public BigDecimal getOrderAmount() {
		return orderAmount;
	}

	public void setOrderAmount(BigDecimal orderAmount) {
		this.orderAmount = orderAmount;
	}

	public String getUserTags() {
		return userTags;
	}

	public void setUserTags(String userTags) {
		this.userTags = userTags;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public static class SkuDetail {

		@NotBlank
		private String skuId;

		@NotNull
		private Integer quantity;

		@NotNull
		private BigDecimal price;

		public String getSkuId() {
			return skuId;
		}

		public void setSkuId(String skuId) {
			this.skuId = skuId;
		}

		public Integer getQuantity() {
			return quantity;
		}

		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}

		public BigDecimal getPrice() {
			return price;
		}

		public void setPrice(BigDecimal price) {
			this.price = price;
		}
	}
}
