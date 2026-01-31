package com.github.spud.tinystore.promotion.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserCoupon {

	private UUID id;
	private String userId;
	private UUID couponId;
	private String couponNo;
	private OffsetDateTime receiveTime;
	private UserCouponStatus useStatus;
	private String lockId;
	private OffsetDateTime lockExpireTime;
	private String usedTradeId;
	private OffsetDateTime usedTime;

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

	public OffsetDateTime getReceiveTime() {
		return receiveTime;
	}

	public void setReceiveTime(OffsetDateTime receiveTime) {
		this.receiveTime = receiveTime;
	}

	public UserCouponStatus getUseStatus() {
		return useStatus;
	}

	public void setUseStatus(UserCouponStatus useStatus) {
		this.useStatus = useStatus;
	}

	public String getLockId() {
		return lockId;
	}

	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	public OffsetDateTime getLockExpireTime() {
		return lockExpireTime;
	}

	public void setLockExpireTime(OffsetDateTime lockExpireTime) {
		this.lockExpireTime = lockExpireTime;
	}

	public String getUsedTradeId() {
		return usedTradeId;
	}

	public void setUsedTradeId(String usedTradeId) {
		this.usedTradeId = usedTradeId;
	}

	public OffsetDateTime getUsedTime() {
		return usedTime;
	}

	public void setUsedTime(OffsetDateTime usedTime) {
		this.usedTime = usedTime;
	}
}
