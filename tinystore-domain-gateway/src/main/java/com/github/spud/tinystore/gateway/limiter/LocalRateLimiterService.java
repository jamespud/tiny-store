package com.github.spud.tinystore.gateway.limiter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;

import reactor.core.publisher.Mono;

@Component
public class LocalRateLimiterService {

	private final Cache<String, LocalBucket> buckets;

	public LocalRateLimiterService(Cache<String, LocalBucket> buckets) {
		this.buckets = buckets;
	}

	public Mono<RateLimitResult> isAllowed(String cacheKey, GatewayRoutesDefinition.RateLimitPolicy policy) {
		return Mono.fromSupplier(() -> {
			LocalBucket bucket = buckets.get(cacheKey,
				key -> new LocalBucket(policy.getCapacity(), policy.getRefillRate(), Instant.now()));
			synchronized (bucket) {
				bucket.refill(policy.getCapacity(), policy.getRefillRate(), Instant.now());
				if (bucket.tokens >= 1) {
					bucket.tokens -= 1;
					return RateLimitResult.allowed(bucket.tokens).withFallback();
				}
				Duration retryAfter = bucket.calculateRetryAfter(policy.getRefillRate());
				return RateLimitResult.rejected(retryAfter, true);
			}
		});
	}

	public static final class LocalBucket {

		private long tokens;
		private Instant lastRefill;

		private LocalBucket(long capacity, long refillRatePerSecond, Instant now) {
			this.tokens = capacity;
			this.lastRefill = now;
		}

		private void refill(long capacity, long refillRatePerSecond, Instant now) {
			if (refillRatePerSecond <= 0) {
				return;
			}
			long secondsSinceLast = Math.max(0, now.getEpochSecond() - lastRefill.getEpochSecond());
			if (secondsSinceLast <= 0) {
				return;
			}
			long refill = secondsSinceLast * refillRatePerSecond;
			if (refill > 0) {
				tokens = Math.min(capacity, tokens + refill);
				lastRefill = now;
			}
		}

		private Duration calculateRetryAfter(long refillRatePerSecond) {
			if (refillRatePerSecond <= 0) {
				return Duration.ofSeconds(1);
			}
			double seconds = Math.ceil(1.0D / refillRatePerSecond);
			seconds = Math.max(1D, seconds);
			return Duration.ofSeconds((long) seconds);
		}
	}
}
