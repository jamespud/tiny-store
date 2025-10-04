package com.github.spud.tinystore.promotion.domain.model;

import java.util.List;

public class CouponEvaluationContext {

	// TODO: Populate fields representing order amount, user, available coupons, etc.
	private String userId;
	private List<String> couponIds;

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
}
