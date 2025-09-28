package com.tinystore.auth.domain.exception;

public class OtpExpiredException extends RuntimeException {

	public OtpExpiredException(String message) {
		super(message);
	}
}