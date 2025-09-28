package com.github.spud.tinystore.gateway.config;

import java.time.Duration;
import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.spud.tinystore.gateway.limiter.LocalRateLimiterService;

@Configuration
public class LocalCacheConfig {

	@Bean
	public Cache<String, LocalRateLimiterService.LocalBucket> localRateLimiterCache() {
		return Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10))
			.maximumSize(10_000)
			.build();
	}

	@Bean
	public Cache<String, Instant> localIdempotencyCache() {
		return Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(10))
			.maximumSize(20_000)
			.build();
	}
}
