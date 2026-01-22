package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.Collections;
import java.util.List;

public class CheckoutQuoteResponse {

	private CheckoutResultStatus status;
	private String quoteId;
	private long expiresAtEpochMs;
	private PricingSnapshot snapshot;
	private List<ChangeReason> changeReasons = Collections.emptyList();

	public CheckoutResultStatus getStatus() {
		return status;
	}

	public void setStatus(CheckoutResultStatus status) {
		this.status = status;
	}

	public String getQuoteId() {
		return quoteId;
	}

	public void setQuoteId(String quoteId) {
		this.quoteId = quoteId;
	}

	public long getExpiresAtEpochMs() {
		return expiresAtEpochMs;
	}

	public void setExpiresAtEpochMs(long expiresAtEpochMs) {
		this.expiresAtEpochMs = expiresAtEpochMs;
	}

	public PricingSnapshot getSnapshot() {
		return snapshot;
	}

	public void setSnapshot(PricingSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public List<ChangeReason> getChangeReasons() {
		return changeReasons;
	}

	public void setChangeReasons(List<ChangeReason> changeReasons) {
		this.changeReasons = changeReasons == null ? Collections.emptyList() : changeReasons;
	}
}

