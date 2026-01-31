package com.github.spud.tinystore.promotion.interfaces.dto;

public class UserCouponView {

	private String userCouponId;
	private String couponId;
	private String couponNo;
	private String useStatus;
	private Long receiveAtEpochMs;
	private String usedTradeId;
	private Long usedAtEpochMs;

	public String getUserCouponId() {
		return userCouponId;
	}

	public void setUserCouponId(String userCouponId) {
		this.userCouponId = userCouponId;
	}

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

	public String getUseStatus() {
		return useStatus;
	}

	public void setUseStatus(String useStatus) {
		this.useStatus = useStatus;
	}

	public Long getReceiveAtEpochMs() {
		return receiveAtEpochMs;
	}

	public void setReceiveAtEpochMs(Long receiveAtEpochMs) {
		this.receiveAtEpochMs = receiveAtEpochMs;
	}

	public String getUsedTradeId() {
		return usedTradeId;
	}

	public void setUsedTradeId(String usedTradeId) {
		this.usedTradeId = usedTradeId;
	}

	public Long getUsedAtEpochMs() {
		return usedAtEpochMs;
	}

	public void setUsedAtEpochMs(Long usedAtEpochMs) {
		this.usedAtEpochMs = usedAtEpochMs;
	}
}

