package com.github.spud.tinystore.promotion.interfaces.dto;

public class CheckoutReleaseResponse {

	private boolean success;
	private String message;

	public static CheckoutReleaseResponse of(boolean success, String message) {
		CheckoutReleaseResponse r = new CheckoutReleaseResponse();
		r.setSuccess(success);
		r.setMessage(message);
		return r;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}

