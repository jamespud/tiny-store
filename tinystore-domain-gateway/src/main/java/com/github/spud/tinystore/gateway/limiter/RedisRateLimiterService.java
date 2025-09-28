package com.github.spud.tinystore.gateway.limiter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;

import reactor.core.publisher.Mono;

@Service
public class RedisRateLimiterService {

	private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);
	private static final String KEY_PREFIX = "tinystore:gateway:ratelimit";

	private final ReactiveStringRedisTemplate redisTemplate;
	private final LocalRateLimiterService localRateLimiterService;
	private final RedisScript<List<Long>> script;

	public RedisRateLimiterService(ReactiveStringRedisTemplate redisTemplate,
		ResourceLoader resourceLoader,
		LocalRateLimiterService localRateLimiterService) {
		this.redisTemplate = redisTemplate;
		this.localRateLimiterService = localRateLimiterService;
		this.script = loadScript(resourceLoader);
	}

	public Mono<RateLimitResult> isAllowed(String routeId, String identity,
		GatewayRoutesDefinition.RateLimitPolicy policy) {
		if (policy == null) {
			return Mono.just(RateLimitResult.allowed(Long.MAX_VALUE));
		}

		List<String> keys = List.of(tokensKey(routeId, identity), timestampKey(routeId, identity));
		String capacity = String.valueOf(policy.getCapacity());
		String refillRate = String.valueOf(policy.getRefillRate());
		String now = String.valueOf(Instant.now().getEpochSecond());

		return redisTemplate.execute(script, keys, capacity, refillRate, now, "1")
			.next()
			.map(result -> toResult(result, false))
			.switchIfEmpty(fallback(routeId, identity, policy))
			.onErrorResume(ex -> {
				log.warn("Redis rate limiter unavailable, falling back to local cache", ex);
				return fallback(routeId, identity, policy);
			});
	}

	private Mono<RateLimitResult> fallback(String routeId, String identity, GatewayRoutesDefinition.RateLimitPolicy policy) {
		String cacheKey = routeId + ":" + identity;
		return localRateLimiterService.isAllowed(cacheKey, policy);
	}

	@SuppressWarnings("unchecked")
	private RedisScript<List<Long>> loadScript(ResourceLoader loader) {
		Resource resource = loader.getResource("classpath:scripts/rate_limiter.lua");
		DefaultRedisScript<List<Long>> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptSource(new ResourceScriptSource(resource));
		redisScript.setResultType((Class<List<Long>>) (Class<?>) List.class);
		return redisScript;
	}

	private RateLimitResult toResult(List<Long> result, boolean fallback) {
		if (Objects.isNull(result) || result.size() < 3) {
			return RateLimitResult.rejected(Duration.ofSeconds(1), fallback);
		}
		boolean allowed = result.get(0) == 1L;
		long remaining = result.get(1);
		Duration retryAfter = Duration.ofSeconds(Math.max(0L, result.get(2)));
		if (allowed) {
			return fallback ? RateLimitResult.allowed(remaining).withFallback()
				: RateLimitResult.allowed(remaining);
		}
		return RateLimitResult.rejected(retryAfter, fallback);
	}

	private String tokensKey(String routeId, String identity) {
		return KEY_PREFIX + ":" + routeId + ":" + identity + ":tokens";
	}

	private String timestampKey(String routeId, String identity) {
		return KEY_PREFIX + ":" + routeId + ":" + identity + ":ts";
	}
}
