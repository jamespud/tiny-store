package com.github.spud.tinystore.gateway.idempotency;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

	@Mock
	private ReactiveStringRedisTemplate redisTemplate;

	@Mock
	private ReactiveValueOperations<String, String> valueOperations;

	@Mock
	private LocalIdempotencyService localIdempotencyService;

	private IdempotencyService idempotencyService;

	@BeforeEach
	void setUp() {
		IdempotencyProperties properties = new IdempotencyProperties();
		properties.setKeyPrefix("idempotent");
		properties.setTtl(Duration.ofMinutes(5));
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		idempotencyService = new IdempotencyService(redisTemplate, localIdempotencyService, properties);
	}

	@Test
	void tryAcquireUsesRedis() {
		when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));

		StepVerifier.create(idempotencyService.tryAcquire("key"))
			.expectNextMatches(result -> result.acquired() && !result.fallback())
			.verifyComplete();

		verify(localIdempotencyService, never()).tryAcquire(any(), any());
	}

	@Test
	void tryAcquireFallsBackWhenRedisFails() {
		when(valueOperations.setIfAbsent(any(), any(), any()))
			.thenReturn(Mono.error(new IllegalStateException("redis down")));
		when(localIdempotencyService.tryAcquire(any(), any()))
			.thenReturn(Mono.just(IdempotencyResult.success().withFallback()));

		StepVerifier.create(idempotencyService.tryAcquire("key"))
			.expectNextMatches(result -> result.acquired() && result.fallback())
			.verifyComplete();
	}

	@Test
	void markSuccessFallsBackWhenRedisFails() {
		when(valueOperations.set(any(), any(), any())).thenReturn(Mono.error(new RuntimeException("fail")));
		when(localIdempotencyService.markSuccess(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(idempotencyService.markSuccess("key"))
			.verifyComplete();

		verify(localIdempotencyService).markSuccess(any(), any());
	}

	@Test
	void releaseFallsBackWhenRedisFails() {
		when(redisTemplate.delete(anyString())).thenReturn(Mono.error(new RuntimeException("fail")));
		when(localIdempotencyService.release(any())).thenReturn(Mono.empty());

		StepVerifier.create(idempotencyService.release("key"))
			.verifyComplete();

		verify(localIdempotencyService).release(any());
	}
}
