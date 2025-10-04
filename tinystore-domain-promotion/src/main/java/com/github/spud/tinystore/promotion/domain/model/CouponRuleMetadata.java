package com.github.spud.tinystore.promotion.domain.model;

import java.math.BigDecimal;

public class CouponRuleMetadata {

	private int priority;
	private String mutexGroup;
	private BigDecimal thresholdAmount;
	private BigDecimal discountAmount;
	private BigDecimal discountRate;
	private BigDecimal maxDiscountAmount;

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public String getMutexGroup() {
		return mutexGroup;
	}

	public void setMutexGroup(String mutexGroup) {
		this.mutexGroup = mutexGroup;
	}

	public BigDecimal getThresholdAmount() {
		return thresholdAmount;
	}

	public void setThresholdAmount(BigDecimal thresholdAmount) {
		this.thresholdAmount = thresholdAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount;
	}

	public BigDecimal getDiscountRate() {
		return discountRate;
	}

	public void setDiscountRate(BigDecimal discountRate) {
		this.discountRate = discountRate;
	}

	public BigDecimal getMaxDiscountAmount() {
		return maxDiscountAmount;
	}

	public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
		this.maxDiscountAmount = maxDiscountAmount;
	}
}
