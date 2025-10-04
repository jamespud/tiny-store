package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.Collections;
import java.util.List;

public class CouponListResponse {

	private List<CouponView> coupons = Collections.emptyList();

	public List<CouponView> getCoupons() {
		return coupons;
	}

	public void setCoupons(List<CouponView> coupons) {
		this.coupons = coupons == null ? Collections.emptyList() : coupons;
	}

	public static class CouponView {

		private String couponId;

		private String couponNo;

		private String couponType;

		private boolean canReceive;

		private String status;

		private String description;

		public String getCouponId() {
			return couponId;
		}

		public void setCouponId(String couponId) {
			this.couponId = couponId;
		}

		public String getCouponNo() {
			return couponNo;
		}

		public void setCouponNo(String couponNo) {
			this.couponNo = couponNo;
		}

		public String getCouponType() {
			return couponType;
		}

		public void setCouponType(String couponType) {
			this.couponType = couponType;
		}

		public boolean isCanReceive() {
			return canReceive;
		}

		public void setCanReceive(boolean canReceive) {
			this.canReceive = canReceive;
	}

		public String getStatus() {
			return status;
	}

		public void setStatus(String status) {
			this.status = status;
	}

		public String getDescription() {
			return description;
	}

		public void setDescription(String description) {
			this.description = description;
	}
	}
}
