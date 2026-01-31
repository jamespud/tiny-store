package com.github.spud.tinystore.promotion.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutCommitRequest {

	@NotBlank
	private String quoteId;

	@NotBlank
	private String tradeId;

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

	public String getTradeId() {
		return tradeId;
	}

	public void setTradeId(String tradeId) {
		this.tradeId = tradeId;
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
