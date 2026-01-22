package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon", schema = "promotion")
public class CouponEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "coupon_no", nullable = false)
	private String couponNo;

	@Column(name = "coupon_type", nullable = false)
	private String couponType;

	@Column(name = "shop_id")
	private String shopId;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "threshold_amount")
	private BigDecimal thresholdAmount;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "discount_amount")
	private BigDecimal discountAmount;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "discount_rate")
	private BigDecimal discountRate;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "max_discount_amount")
	private BigDecimal maxDiscountAmount;

	@Column(name = "total_stock", nullable = false)
	private long totalStock;

	@Column(name = "used_stock", nullable = false)
	private long usedStock;

	@Column(name = "start_time", nullable = false)
	private LocalDateTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalDateTime endTime;

	@Column(name = "mutex_group")
	private String mutexGroup;

	@Column(name = "priority", nullable = false)
	private int priority;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "budget_type")
	private String budgetType;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "budget_total")
	private BigDecimal budgetTotal;

	@JdbcTypeCode(SqlTypes.NUMERIC)
	@Column(name = "budget_used")
	private BigDecimal budgetUsed;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

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

	public String getCouponType() {
		return couponType;
	}

	public void setCouponType(String couponType) {
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

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getBudgetType() {
		return budgetType;
	}

	public void setBudgetType(String budgetType) {
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}

