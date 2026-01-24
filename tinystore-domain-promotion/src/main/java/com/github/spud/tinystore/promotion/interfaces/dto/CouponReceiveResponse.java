package com.github.spud.tinystore.promotion.interfaces.dto;

public class CouponReceiveResponse {

	private CouponReceiveStatus status;
	private String taskId;
	private String couponId;
	private String message;

	public static CouponReceiveResponse of(CouponReceiveStatus status, String taskId, String couponId, String message) {
		CouponReceiveResponse r = new CouponReceiveResponse();
		r.setStatus(status);
		r.setTaskId(taskId);
		r.setCouponId(couponId);
		r.setMessage(message);
		return r;
	}

	public CouponReceiveStatus getStatus() {
		return status;
	}

	public void setStatus(CouponReceiveStatus status) {
		this.status = status;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getCouponId() {
		return couponId;
	}

	public void setCouponId(String couponId) {
		this.couponId = couponId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}

