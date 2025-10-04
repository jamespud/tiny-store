package com.github.spud.tinystore.promotion.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class CouponEvaluationResult {

	private final boolean valid;
	private final List<String> appliedCoupons;
	private final BigDecimal totalDiscount;

	public CouponEvaluationResult(boolean valid, List<String> appliedCoupons, BigDecimal totalDiscount) {
		this.valid = valid;
		this.appliedCoupons = appliedCoupons == null ? Collections.emptyList() : appliedCoupons;
		this.totalDiscount = totalDiscount;
	}

	public boolean isValid() {
		return valid;
	}

	public List<String> getAppliedCoupons() {
		return appliedCoupons;
	}

	public BigDecimal getTotalDiscount() {
		return totalDiscount;
	}
}
