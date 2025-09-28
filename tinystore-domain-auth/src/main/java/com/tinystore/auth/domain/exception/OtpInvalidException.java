package com.tinystore.auth.domain.exception;

public class OtpInvalidException extends RuntimeException {

	public OtpInvalidException(String message) {
		super(message);
	}
}