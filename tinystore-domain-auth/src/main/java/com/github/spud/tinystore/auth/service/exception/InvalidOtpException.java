package com.github.spud.tinystore.auth.service.exception;

public class InvalidOtpException extends RuntimeException {
	public InvalidOtpException(String message) {
		super(message);
	}
}
