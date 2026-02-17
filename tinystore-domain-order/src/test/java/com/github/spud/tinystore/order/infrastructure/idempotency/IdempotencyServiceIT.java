package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import com.github.spud.tinystore.order.test.it.AbstractOrderIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IdempotencyService Integration Test
 *
 * Coverage:
 * - Real Redis container with Testcontainers
 * - Redis stop simulation
 * - Verify exception thrown when Redis unavailable
 * - Verify success path with real Redis
 */
@SpringBootTest(
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "order.idempotency.fail-on-redis-error=true"
    }
)
@Testcontainers
@DisplayName("IdempotencyService Integration Tests")
class IdempotencyServiceIT extends AbstractOrderIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("tryAcquire - success path with real Redis")
    void tryAcquire_withRealRedis_success() {
        // Given: Redis is running
        String idempotencyKey = "test-key-" + System.currentTimeMillis();
        String fingerprint = "fingerprint-001";

        // When: tryAcquire for the first time
        boolean firstAttempt = idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint);

        // Then: returns true (lock acquired)
        assertThat(firstAttempt).isTrue();

        // When: tryAcquire again with same key
        boolean secondAttempt = idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint);

        // Then: returns false (lock already exists)
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("getCachedResponse and storeResponse - success path with real Redis")
    void getCachedResponseAndStoreResponse_withRealRedis_success() {
        // Given: Redis is running
        String idempotencyKey = "test-key-" + System.currentTimeMillis();
        String responseJson = "{\"tradeId\":\"trade-001\",\"payableAmountCents\":9900}";

        // When: getCachedResponse before storing (should return null)
        String cachedBefore = idempotencyService.getCachedResponse("trade:create", idempotencyKey);

        // Then: returns null
        assertThat(cachedBefore).isNull();

        // When: storeResponse
        idempotencyService.storeResponse("trade:create", idempotencyKey, responseJson);

        // When: getCachedResponse after storing
        String cachedAfter = idempotencyService.getCachedResponse("trade:create", idempotencyKey);

        // Then: returns stored response
        assertThat(cachedAfter).isEqualTo(responseJson);
    }

    @Test
    @DisplayName("tryAcquire - throws exception when Redis is stopped")
    void tryAcquire_whenRedisDown_throwsException() {
        // Given: Stop Redis container
        redis.stop();

        try {
            // When & Then: expect IdempotencyServiceUnavailableException
            String idempotencyKey = "test-key-down-" + System.currentTimeMillis();
            assertThatThrownBy(() ->
                idempotencyService.tryAcquire("trade:create", idempotencyKey, "fingerprint-002")
            )
                .isInstanceOf(IdempotencyServiceUnavailableException.class)
                .hasMessageContaining("tryAcquire")
                .hasMessageContaining(idempotencyKey);
        } finally {
            // Restart Redis for subsequent tests
            redis.start();
        }
    }

    @Test
    @DisplayName("getCachedResponse - throws exception when Redis is stopped")
    void getCachedResponse_whenRedisDown_throwsException() {
        // Given: Stop Redis container
        redis.stop();

        try {
            // When & Then: expect IdempotencyServiceUnavailableException
            String idempotencyKey = "test-key-down-" + System.currentTimeMillis();
            assertThatThrownBy(() ->
                idempotencyService.getCachedResponse("trade:create", idempotencyKey)
            )
                .isInstanceOf(IdempotencyServiceUnavailableException.class)
                .hasMessageContaining("getCachedResponse")
                .hasMessageContaining(idempotencyKey);
        } finally {
            // Restart Redis for subsequent tests
            redis.start();
        }
    }

    @Test
    @DisplayName("storeResponse - throws exception when Redis is stopped")
    void storeResponse_whenRedisDown_throwsException() {
        // Given: Stop Redis container
        redis.stop();

        try {
            // When & Then: expect IdempotencyServiceUnavailableException
            String idempotencyKey = "test-key-down-" + System.currentTimeMillis();
            assertThatThrownBy(() ->
                idempotencyService.storeResponse("trade:create", idempotencyKey, "{\"tradeId\":\"trade-002\"}")
            )
                .isInstanceOf(IdempotencyServiceUnavailableException.class)
                .hasMessageContaining("storeResponse")
                .hasMessageContaining(idempotencyKey);
        } finally {
            // Restart Redis for subsequent tests
            redis.start();
        }
    }

    @Test
    @DisplayName("releaseLock - success path with real Redis")
    void releaseLock_withRealRedis_success() {
        // Given: Redis is running and lock exists
        String idempotencyKey = "test-key-" + System.currentTimeMillis();
        String fingerprint = "fingerprint-003";
        idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint);

        // When: releaseLock
        idempotencyService.releaseLock("trade:create", idempotencyKey);

        // Then: lock can be acquired again
        boolean canAcquireAgain = idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint);
        assertThat(canAcquireAgain).isTrue();
    }
}
