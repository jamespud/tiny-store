package com.github.spud.tinystore.promotion.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutCommitRequest {

	@NotBlank
	private String quoteId;

	@NotBlank
	private String orderNo;

	@NotBlank
	private String inputHash;

	private String payNo;

	private Long paidAt;

	public String getQuoteId() {
		return quoteId;
	}

	public void setQuoteId(String quoteId) {
		this.quoteId = quoteId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
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
