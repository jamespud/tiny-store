package com.github.spud.tinystore.gateway.limiter;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.spud.tinystore.gateway.config.GatewayRoutesDefinition;
import com.github.spud.tinystore.gateway.config.LocalCacheConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterServiceIT {

	@Mock
	private ReactiveStringRedisTemplate redisTemplate;

	@Container
	static final GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
		.withExposedPorts(6379);

	private LocalRateLimiterService localRateLimiterService;
	private RedisRateLimiterService redisRateLimiterService;
	private GatewayRoutesDefinition.RateLimitPolicy policy;

	@DynamicPropertySource
	static void registerProps(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379).toString());
	}

	@BeforeEach
	void setUp() {
		Cache<String, LocalRateLimiterService.LocalBucket> cache = new LocalCacheConfig().localRateLimiterCache();
		localRateLimiterService = new LocalRateLimiterService(cache);
		redisRateLimiterService = new RedisRateLimiterService(redisTemplate, new DefaultResourceLoader(), localRateLimiterService);
		policy = new GatewayRoutesDefinition.RateLimitPolicy();
		policy.setCapacity(1);
		policy.setRefillRate(1);
	}

	@Test
	void shouldAllowWhenRedisScriptPermits() {
		List<Long> scriptResult = List.of(1L, 0L, 0L);
		when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<List<Long>>>any(), anyList(), any(), any(), any(), any()))
			.thenReturn(Flux.<List<Long>>just(scriptResult));

		StepVerifier.create(redisRateLimiterService.isAllowed("route", "ip", policy))
			.assertNext(result -> assertThat(result.allowed()).isTrue())
			.verifyComplete();
	}

	@Test
	void shouldFallbackWhenRedisFails() {
		when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<List<Long>>>any(), anyList(), any(), any(), any(), any()))
			.thenReturn(Flux.error(new RuntimeException("redis down")));

		StepVerifier.create(redisRateLimiterService.isAllowed("route", "ip", policy))
			.assertNext(result -> assertThat(result.fallback()).isTrue())
			.verifyComplete();
	}
}
