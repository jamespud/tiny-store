package com.github.spud.tinystore.promotion.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ConfirmUseRequest {

	@NotBlank
	private String orderNo;

	@NotBlank
	private String lockId;

	@NotBlank
	private String payNo;

	@NotNull
	private Long paidAt;

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getLockId() {
		return lockId;
	}

	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	public String getPayNo() {
		return payNo;
	}

	public void setPayNo(String payNo) {
		this.payNo = payNo;
	}

	public Long getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(Long paidAt) {
		this.paidAt = paidAt;
	}
}
