package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyService Unit Test
 *
 * Coverage:
 * - tryAcquire with Redis connection failure
 * - getCachedResponse with Redis connection failure
 * - storeResponse with Redis connection failure
 * - Degraded mode (fail-on-redis-error=false)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Unit Tests")
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        // 手动构造（需要 MeterRegistry）
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        idempotencyService = new IdempotencyService(meterRegistry);

        // Set dependencies
        ReflectionTestUtils.setField(idempotencyService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(idempotencyService, "ttlSeconds", 600L);
        ReflectionTestUtils.setField(idempotencyService, "failOnRedisError", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("tryAcquire - Redis connection failure throws IdempotencyServiceUnavailableException")
    void tryAcquire_redisConnectionFailure_throwsException() {
        // Given: Redis throws connection failure
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then: expect IdempotencyServiceUnavailableException
        assertThatThrownBy(() ->
            idempotencyService.tryAcquire("trade:create", "idem-key-001", "fingerprint-001")
        )
            .isInstanceOf(IdempotencyServiceUnavailableException.class)
            .hasMessageContaining("tryAcquire")
            .hasMessageContaining("idem-key-001")
            .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("tryAcquire - Redis system exception throws IdempotencyServiceUnavailableException")
    void tryAcquire_redisSystemException_throwsException() {
        // Given: Redis throws system exception
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RedisSystemException("Redis system error", new RuntimeException("Internal error")));

        // When & Then: expect IdempotencyServiceUnavailableException
        assertThatThrownBy(() ->
            idempotencyService.tryAcquire("trade:create", "idem-key-002", "fingerprint-002")
        )
            .isInstanceOf(IdempotencyServiceUnavailableException.class)
            .hasMessageContaining("tryAcquire")
            .hasCauseInstanceOf(RedisSystemException.class);
    }

    @Test
    @DisplayName("tryAcquire - degraded mode (fail-on-redis-error=false) returns true on Redis failure")
    void tryAcquire_degradedMode_returnsTrueOnRedisFailure() {
        // Given: degraded mode enabled and Redis fails
        ReflectionTestUtils.setField(idempotencyService, "failOnRedisError", false);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When: tryAcquire
        boolean result = idempotencyService.tryAcquire("trade:create", "idem-key-003", "fingerprint-003");

        // Then: returns fallback value (true = allow request)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getCachedResponse - Redis connection failure throws IdempotencyServiceUnavailableException")
    void getCachedResponse_redisConnectionFailure_throwsException() {
        // Given: Redis throws connection failure
        when(valueOperations.get(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then: expect IdempotencyServiceUnavailableException
        assertThatThrownBy(() ->
            idempotencyService.getCachedResponse("trade:create", "idem-key-004")
        )
            .isInstanceOf(IdempotencyServiceUnavailableException.class)
            .hasMessageContaining("getCachedResponse")
            .hasMessageContaining("idem-key-004")
            .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("getCachedResponse - degraded mode returns null on Redis failure")
    void getCachedResponse_degradedMode_returnsNullOnRedisFailure() {
        // Given: degraded mode enabled and Redis fails
        ReflectionTestUtils.setField(idempotencyService, "failOnRedisError", false);
        when(valueOperations.get(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When: getCachedResponse
        String result = idempotencyService.getCachedResponse("trade:create", "idem-key-005");

        // Then: returns fallback value (null)
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("storeResponse - Redis connection failure throws IdempotencyServiceUnavailableException")
    void storeResponse_redisConnectionFailure_throwsException() {
        // Given: Redis throws connection failure
        doThrow(new RedisConnectionFailureException("Connection refused"))
            .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // When & Then: expect IdempotencyServiceUnavailableException
        assertThatThrownBy(() ->
            idempotencyService.storeResponse("trade:create", "idem-key-006", "{\"tradeId\":\"trade-001\"}")
        )
            .isInstanceOf(IdempotencyServiceUnavailableException.class)
            .hasMessageContaining("storeResponse")
            .hasMessageContaining("idem-key-006")
            .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("storeResponse - degraded mode silently fails on Redis failure")
    void storeResponse_degradedMode_silentlyFailsOnRedisFailure() {
        // Given: degraded mode enabled and Redis fails
        ReflectionTestUtils.setField(idempotencyService, "failOnRedisError", false);
        doThrow(new RedisConnectionFailureException("Connection refused"))
            .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // When & Then: no exception thrown (silent failure in degraded mode)
        idempotencyService.storeResponse("trade:create", "idem-key-007", "{\"tradeId\":\"trade-002\"}");
    }

    @Test
    @DisplayName("tryAcquire - success path returns true when lock acquired")
    void tryAcquire_successPath_returnsTrueWhenLockAcquired() {
        // Given: Redis returns true (lock acquired)
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(true);

        // When: tryAcquire
        boolean result = idempotencyService.tryAcquire("trade:create", "idem-key-008", "fingerprint-008");

        // Then: returns true
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("tryAcquire - returns false when lock already exists")
    void tryAcquire_returnsFlaseWhenLockAlreadyExists() {
        // Given: Redis returns false (lock already exists)
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
            .thenReturn(false);

        // When: tryAcquire
        boolean result = idempotencyService.tryAcquire("trade:create", "idem-key-009", "fingerprint-009");

        // Then: returns false
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getCachedResponse - success path returns cached value")
    void getCachedResponse_successPath_returnsCachedValue() {
        // Given: Redis returns cached response
        String cachedResponse = "{\"tradeId\":\"trade-003\",\"payableAmountCents\":9900}";
        when(valueOperations.get(anyString())).thenReturn(cachedResponse);

        // When: getCachedResponse
        String result = idempotencyService.getCachedResponse("trade:create", "idem-key-010");

        // Then: returns cached response
        assertThat(result).isEqualTo(cachedResponse);
    }

    @Test
    @DisplayName("storeResponse - success path stores response in Redis")
    void storeResponse_successPath_storesResponseInRedis() {
        // Given: Redis set operation succeeds
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // When: storeResponse
        idempotencyService.storeResponse("trade:create", "idem-key-011", "{\"tradeId\":\"trade-004\"}");

        // Then: verify Redis set was called
        verify(valueOperations).set(
            eq("idempotency:response:trade:create:idem-key-011"),
            eq("{\"tradeId\":\"trade-004\"}"),
            eq(600L),
            eq(TimeUnit.SECONDS)
        );
    }
}
