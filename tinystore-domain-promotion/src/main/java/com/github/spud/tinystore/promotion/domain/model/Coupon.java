package com.github.spud.tinystore.promotion.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Coupon {

	private UUID id;
	private String couponNo;
	private CouponType couponType;
	private String shopId;
	private BigDecimal thresholdAmount;
	private BigDecimal discountAmount;
	private BigDecimal discountRate;
	private BigDecimal maxDiscountAmount;
	private long totalStock;
	private long usedStock;
	private OffsetDateTime startTime;
	private OffsetDateTime endTime;
	private String mutexGroup;
	private int priority;
	private CouponStatus status;
	private BudgetType budgetType;
	private BigDecimal budgetTotal;
	private BigDecimal budgetUsed;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCouponNo() {
		return couponNo;
	}

	public void setCouponNo(String couponNo) {
		this.couponNo = couponNo;
	}

	public CouponType getCouponType() {
		return couponType;
	}

	public void setCouponType(CouponType couponType) {
		this.couponType = couponType;
	}

	public String getShopId() {
		return shopId;
	}

	public void setShopId(String shopId) {
		this.shopId = shopId;
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

	public long getTotalStock() {
		return totalStock;
	}

	public void setTotalStock(long totalStock) {
		this.totalStock = totalStock;
	}

	public long getUsedStock() {
		return usedStock;
	}

	public void setUsedStock(long usedStock) {
		this.usedStock = usedStock;
	}

	public OffsetDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(OffsetDateTime startTime) {
		this.startTime = startTime;
	}

	public OffsetDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(OffsetDateTime endTime) {
		this.endTime = endTime;
	}

	public String getMutexGroup() {
		return mutexGroup;
	}

	public void setMutexGroup(String mutexGroup) {
		this.mutexGroup = mutexGroup;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public CouponStatus getStatus() {
		return status;
	}

	public void setStatus(CouponStatus status) {
		this.status = status;
	}

	public BudgetType getBudgetType() {
		return budgetType;
	}

	public void setBudgetType(BudgetType budgetType) {
		this.budgetType = budgetType;
	}

	public BigDecimal getBudgetTotal() {
		return budgetTotal;
	}

	public void setBudgetTotal(BigDecimal budgetTotal) {
		this.budgetTotal = budgetTotal;
	}

	public BigDecimal getBudgetUsed() {
		return budgetUsed;
	}

	public void setBudgetUsed(BigDecimal budgetUsed) {
		this.budgetUsed = budgetUsed;
	}
}
