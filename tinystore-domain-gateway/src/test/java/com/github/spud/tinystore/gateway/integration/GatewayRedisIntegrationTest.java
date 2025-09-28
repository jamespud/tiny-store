package com.github.spud.tinystore.gateway.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;
import com.github.spud.tinystore.gateway.config.LocalCacheConfig;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyProperties;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyResult;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyService;
import com.github.spud.tinystore.gateway.idempotency.LocalIdempotencyService;
import com.github.spud.tinystore.gateway.limiter.LocalRateLimiterService;
import com.github.spud.tinystore.gateway.limiter.RateLimitResult;
import com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.test.StepVerifier;

@Testcontainers
class GatewayRedisIntegrationTest {

	@SuppressWarnings("resource")
	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
		.withExposedPorts(6379);

	private ReactiveRedisConnectionFactory connectionFactory;
	private ReactiveStringRedisTemplate redisTemplate;
	private IdempotencyService idempotencyService;
	private RedisRateLimiterService rateLimiterService;

	@BeforeEach
	void setUp() {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
		factory.afterPropertiesSet();
		this.connectionFactory = factory;
		this.redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);

		LocalCacheConfig cacheConfig = new LocalCacheConfig();
		LocalIdempotencyService localIdempotencyService = new LocalIdempotencyService(cacheConfig.localIdempotencyCache());
		LocalRateLimiterService localRateLimiterService = new LocalRateLimiterService(cacheConfig.localRateLimiterCache());

		IdempotencyProperties properties = new IdempotencyProperties();
		properties.setKeyPrefix("idempotent");

		this.idempotencyService = new IdempotencyService(redisTemplate, localIdempotencyService, properties);
		this.rateLimiterService = new RedisRateLimiterService(redisTemplate, new DefaultResourceLoader(), localRateLimiterService);

		redisTemplate.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
	}

	@AfterEach
	void tearDown() {
		if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
			lettuce.destroy();
		}
	}

	@Test
	void idempotencyShouldRejectDuplicateKeys() {
		StepVerifier.create(idempotencyService.tryAcquire("route:key"))
			.expectNextMatches(IdempotencyResult::acquired)
			.verifyComplete();

		StepVerifier.create(idempotencyService.tryAcquire("route:key"))
			.expectNextMatches(result -> !result.acquired())
			.verifyComplete();
	}

	@Test
	void rateLimiterShouldDenyWhenCapacityExceeded() {
		GatewayRoutesDefinition.RateLimitPolicy policy = new GatewayRoutesDefinition.RateLimitPolicy();
		policy.setCapacity(1);
		policy.setRefillRate(1);

		RateLimitResult first = rateLimiterService.isAllowed("route", "identity", policy).block();
		RateLimitResult second = rateLimiterService.isAllowed("route", "identity", policy).block();

		assertThat(first).isNotNull();
		assertThat(first.allowed()).isTrue();
		assertThat(second).isNotNull();
		assertThat(second.allowed()).isFalse();
	}
}
