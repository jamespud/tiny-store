package com.github.spud.tinystore.gateway.idempotency;

public record IdempotencyResult(boolean acquired, boolean fallback) {

	public static IdempotencyResult success() {
		return new IdempotencyResult(true, false);
	}

	public static IdempotencyResult duplicate() {
		return new IdempotencyResult(false, false);
	}

	public IdempotencyResult withFallback() {
		return new IdempotencyResult(this.acquired, true);
	}
}
