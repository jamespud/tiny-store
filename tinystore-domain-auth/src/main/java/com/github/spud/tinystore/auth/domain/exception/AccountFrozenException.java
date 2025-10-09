package com.github.spud.tinystore.auth.domain.exception;

public class AccountFrozenException extends RuntimeException {
	public AccountFrozenException(String message) {
		super(message);
	}
}
