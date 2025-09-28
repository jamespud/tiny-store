package com.tinystore.auth.domain.exception;

public class UserFrozenException extends RuntimeException {

	public UserFrozenException(String message) {
		super(message);
	}
}