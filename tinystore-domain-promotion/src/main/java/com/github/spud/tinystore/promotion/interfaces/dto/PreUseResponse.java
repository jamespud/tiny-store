package com.github.spud.tinystore.promotion.interfaces.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class PreUseResponse {

	private boolean valid;

	private List<AppliedCoupon> appliedCoupons = Collections.emptyList();

	private BigDecimal totalDiscount;

	private String lockId;

	private String invalidReason;

	public boolean isValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public List<AppliedCoupon> getAppliedCoupons() {
		return appliedCoupons;
	}

	public void setAppliedCoupons(List<AppliedCoupon> appliedCoupons) {
		this.appliedCoupons = appliedCoupons == null ? Collections.emptyList() : appliedCoupons;
	}

	public BigDecimal getTotalDiscount() {
		return totalDiscount;
	}

	public void setTotalDiscount(BigDecimal totalDiscount) {
		this.totalDiscount = totalDiscount;
	}

	public String getLockId() {
		return lockId;
	}

	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	public String getInvalidReason() {
		return invalidReason;
	}

	public void setInvalidReason(String invalidReason) {
		this.invalidReason = invalidReason;
	}

	public static class AppliedCoupon {

		private String couponId;

		private BigDecimal discountAmount;

		private String ruleTrace;

		public String getCouponId() {
			return couponId;
		}

		public void setCouponId(String couponId) {
			this.couponId = couponId;
		}

		public BigDecimal getDiscountAmount() {
			return discountAmount;
		}

		public void setDiscountAmount(BigDecimal discountAmount) {
			this.discountAmount = discountAmount;
		}

		public String getRuleTrace() {
			return ruleTrace;
		}

		public void setRuleTrace(String ruleTrace) {
			this.ruleTrace = ruleTrace;
		}
	}
}
