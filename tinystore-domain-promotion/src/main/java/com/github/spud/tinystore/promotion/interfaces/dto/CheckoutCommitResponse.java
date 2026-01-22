package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.Collections;
import java.util.List;

public class CheckoutCommitResponse {

	private CheckoutResultStatus status;
	private String finalQuoteId;
	private PricingSnapshot snapshot;
	private List<ChangeReason> changeReasons = Collections.emptyList();
	private String message;

	public CheckoutResultStatus getStatus() {
		return status;
	}

	public void setStatus(CheckoutResultStatus status) {
		this.status = status;
	}

	public String getFinalQuoteId() {
		return finalQuoteId;
	}

	public void setFinalQuoteId(String finalQuoteId) {
		this.finalQuoteId = finalQuoteId;
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

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}

