package com.github.spud.tinystore.promotion.interfaces.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class ConfirmUseResponse {

	private boolean success;

	private List<PreUseResponse.AppliedCoupon> appliedCoupons = Collections.emptyList();

	private BigDecimal totalDiscount;

	private String message;

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public List<PreUseResponse.AppliedCoupon> getAppliedCoupons() {
		return appliedCoupons;
	}

	public void setAppliedCoupons(List<PreUseResponse.AppliedCoupon> appliedCoupons) {
		this.appliedCoupons = appliedCoupons == null ? Collections.emptyList() : appliedCoupons;
	}

	public BigDecimal getTotalDiscount() {
		return totalDiscount;
	}

	public void setTotalDiscount(BigDecimal totalDiscount) {
		this.totalDiscount = totalDiscount;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
