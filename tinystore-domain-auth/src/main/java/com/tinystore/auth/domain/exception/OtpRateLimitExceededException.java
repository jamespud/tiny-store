package com.tinystore.auth.domain.exception;

public class OtpRateLimitExceededException extends RuntimeException {

	public OtpRateLimitExceededException(String message) {
		super(message);
	}
}