package com.github.spud.tinystore.gateway.limiter;

import java.time.Duration;

public record RateLimitResult(boolean allowed, long remainingTokens, Duration retryAfter, boolean fallback) {

	public static RateLimitResult allowed(long remainingTokens) {
		return new RateLimitResult(true, remainingTokens, Duration.ZERO, false);
	}

	public static RateLimitResult rejected(Duration retryAfter, boolean fallback) {
		return new RateLimitResult(false, 0, retryAfter, fallback);
	}

	public RateLimitResult withFallback() {
		return new RateLimitResult(this.allowed, this.remainingTokens, this.retryAfter, true);
	}
}
