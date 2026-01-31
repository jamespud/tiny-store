package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_coupon", schema = "promotion")
public class UserCouponEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "coupon_id", nullable = false)
	private UUID couponId;

	@Column(name = "coupon_no", nullable = false)
	private String couponNo;

	@Column(name = "receive_time", nullable = false)
	private LocalDateTime receiveTime;

	@Column(name = "use_status", nullable = false)
	private String useStatus;

	@Column(name = "lock_id")
	private String lockId;

	@Column(name = "lock_expire_time")
	private LocalDateTime lockExpireTime;

	@Column(name = "used_trade_id")
	private String usedTradeId;

	@Column(name = "used_time")
	private LocalDateTime usedTime;

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

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public UUID getCouponId() {
		return couponId;
	}

	public void setCouponId(UUID couponId) {
		this.couponId = couponId;
	}

	public String getCouponNo() {
		return couponNo;
	}

	public void setCouponNo(String couponNo) {
		this.couponNo = couponNo;
	}

	public LocalDateTime getReceiveTime() {
		return receiveTime;
	}

	public void setReceiveTime(LocalDateTime receiveTime) {
		this.receiveTime = receiveTime;
	}

	public String getUseStatus() {
		return useStatus;
	}

	public void setUseStatus(String useStatus) {
		this.useStatus = useStatus;
	}

	public String getLockId() {
		return lockId;
	}

	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	public LocalDateTime getLockExpireTime() {
		return lockExpireTime;
	}

	public void setLockExpireTime(LocalDateTime lockExpireTime) {
		this.lockExpireTime = lockExpireTime;
	}

	public String getUsedTradeId() {
		return usedTradeId;
	}

	public void setUsedTradeId(String usedTradeId) {
		this.usedTradeId = usedTradeId;
	}

	public LocalDateTime getUsedTime() {
		return usedTime;
	}

	public void setUsedTime(LocalDateTime usedTime) {
		this.usedTime = usedTime;
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

