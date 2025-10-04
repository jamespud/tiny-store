package com.github.spud.tinystore.promotion.interfaces.dto;

import java.util.Objects;

public class ReceiveCouponResponse {

	private boolean success;

	private String message;

	private Long remainingStock;

	public static ReceiveCouponResponse of(boolean success, String message, Long remainingStock) {
		ReceiveCouponResponse response = new ReceiveCouponResponse();
		response.setSuccess(success);
		response.setMessage(message);
		response.setRemainingStock(remainingStock);
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

	public Long getRemainingStock() {
		return remainingStock;
	}

	public void setRemainingStock(Long remainingStock) {
		this.remainingStock = remainingStock;
	}

	@Override
	public String toString() {
		return "ReceiveCouponResponse{" +
			"success=" + success +
			", message='" + message + '\'' +
			", remainingStock=" + remainingStock +
			'}';
	}

	@Override
	public int hashCode() {
		return Objects.hash(success, message, remainingStock);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ReceiveCouponResponse that = (ReceiveCouponResponse) obj;
		return success == that.success &&
			Objects.equals(message, that.message) &&
			Objects.equals(remainingStock, that.remainingStock);
	}
}
