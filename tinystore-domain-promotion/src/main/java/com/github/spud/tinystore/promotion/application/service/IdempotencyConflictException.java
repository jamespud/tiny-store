package com.github.spud.tinystore.promotion.application.service;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String message) {
		super(message);
	}
}
