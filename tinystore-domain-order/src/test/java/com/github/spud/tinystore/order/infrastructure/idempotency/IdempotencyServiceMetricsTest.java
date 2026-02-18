package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IdempotencyService Metrics Unit Test
 *
 * 验证当 Redis 不可用且 fail-on-redis-error=true 时，counter 会递增
 */
class IdempotencyServiceMetricsTest {

    private IdempotencyService idempotencyService;
    private StringRedisTemplate redisTemplate;
    private MeterRegistry meterRegistry;
    private Counter counter;

    @BeforeEach
    void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        meterRegistry = new SimpleMeterRegistry();

        idempotencyService = new IdempotencyService(meterRegistry);
        ReflectionTestUtils.setField(idempotencyService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(idempotencyService, "failOnRedisError", true);
        ReflectionTestUtils.setField(idempotencyService, "ttlSeconds", 600L);

        // 获取注册的 counter
        counter = meterRegistry.find("tinystore.idempotency.unavailable.total").counter();
        assertThat(counter).isNotNull();
    }

    @Test
    void tryAcquire_whenRedisFailsAndFailOnErrorTrue_shouldIncrementCounter() {
        // Given: Redis 抛出连接失败异常
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        double countBefore = counter.count();

        // When: 调用 tryAcquire
        assertThatThrownBy(() -> idempotencyService.tryAcquire("order:create", "test-key", "test-fp"))
            .isInstanceOf(IdempotencyServiceUnavailableException.class);

        // Then: counter 递增
        assertThat(counter.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void getCachedResponse_whenRedisFailsAndFailOnErrorTrue_shouldIncrementCounter() {
        // Given: Redis 抛出连接失败异常
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString()))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        double countBefore = counter.count();

        // When: 调用 getCachedResponse
        assertThatThrownBy(() -> idempotencyService.getCachedResponse("order:create", "test-key"))
            .isInstanceOf(IdempotencyServiceUnavailableException.class);

        // Then: counter 递增
        assertThat(counter.count()).isEqualTo(countBefore + 1);
    }
}
