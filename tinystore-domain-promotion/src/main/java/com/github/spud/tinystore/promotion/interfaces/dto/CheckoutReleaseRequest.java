package com.github.spud.tinystore.promotion.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutReleaseRequest {

	@NotBlank
	private String quoteId;

	private String tradeId;

	private String reason;

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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}

