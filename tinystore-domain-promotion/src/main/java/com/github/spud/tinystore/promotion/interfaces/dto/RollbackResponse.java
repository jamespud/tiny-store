package com.github.spud.tinystore.promotion.interfaces.dto;

public class RollbackResponse {

	private boolean success;

	private String message;

	public static RollbackResponse success(String message) {
		RollbackResponse response = new RollbackResponse();
		response.setSuccess(true);
		response.setMessage(message);
		return response;
	}

	public static RollbackResponse failure(String message) {
		RollbackResponse response = new RollbackResponse();
		response.setSuccess(false);
		response.setMessage(message);
		return response;
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
