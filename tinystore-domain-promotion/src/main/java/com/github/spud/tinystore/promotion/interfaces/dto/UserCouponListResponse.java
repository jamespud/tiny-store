package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.List;

public class UserCouponListResponse {

	private String userId;
	private List<UserCouponView> coupons;

	public static UserCouponListResponse of(String userId, List<UserCouponView> coupons) {
		UserCouponListResponse r = new UserCouponListResponse();
		r.setUserId(userId);
		r.setCoupons(coupons);
		return r;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<UserCouponView> getCoupons() {
		return coupons;
	}

	public void setCoupons(List<UserCouponView> coupons) {
		this.coupons = coupons;
	}
}

