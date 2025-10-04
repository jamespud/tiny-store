package com.github.spud.tinystore.promotion.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class ReceiveCouponRequest {

	@NotBlank
	private String userId;

	@NotBlank
	private String couponId;

	private String traceId;

	private String userTags;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getCouponId() {
		return couponId;
	}

	public void setCouponId(String couponId) {
		this.couponId = couponId;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public String getUserTags() {
		return userTags;
	}

	public void setUserTags(String userTags) {
		this.userTags = userTags;
	}
}
