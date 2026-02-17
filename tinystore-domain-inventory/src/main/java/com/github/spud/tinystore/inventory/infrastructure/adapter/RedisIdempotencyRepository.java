package com.github.spud.tinystore.inventory.infrastructure.adapter;

import com.github.spud.tinystore.infrastructure.tool.JsonUtils;
import com.github.spud.tinystore.inventory.domain.port.IdempotencyRepository;
import com.github.spud.tinystore.inventory.domain.value.DeductResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 幂等仓储 Redis 实现（基础设施层适配器）
 * <p>
 * 使用 StringRedisTemplate 缓存扣减/释放的幂等结果。
 */
@Slf4j
@Component
public class RedisIdempotencyRepository implements IdempotencyRepository {

    private static final String DEDUCT_PREFIX = "idem:deduct:";
    private static final String RELEASE_PREFIX = "idem:release:";
    private static final String DEDUCT_ORDER_PREFIX = "idem:deduct:order:";
    private static final String KAFKA_DEDUCT_PREFIX = "idem:kafka:deduct:";
    private static final String KAFKA_RELEASE_PREFIX = "idem:kafka:release:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<DeductResult> getDeductResult(String idempotencyKey) {
        String key = DEDUCT_PREFIX + idempotencyKey;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JsonUtils.fromJson(json, DeductResult.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize deduct idempotency result: key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveDeductResult(String idempotencyKey, DeductResult result) {
        String key = DEDUCT_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, JsonUtils.toJson(result), TTL_HOURS, TimeUnit.HOURS);
    }

    @Override
    public boolean exists(String idempotencyKey) {
        // 检查 deduct 或 release 幂等键
        return Boolean.TRUE.equals(redisTemplate.hasKey(DEDUCT_PREFIX + idempotencyKey))
                || Boolean.TRUE.equals(redisTemplate.hasKey(RELEASE_PREFIX + idempotencyKey));
    }

    @Override
    public boolean isReleased(String idempotencyKey) {
        // 仅检查 release 幂等键（避免被 deduct 幂等短路）
        return Boolean.TRUE.equals(redisTemplate.hasKey(RELEASE_PREFIX + idempotencyKey));
    }

    @Override
    public void markReleased(String idempotencyKey) {
        String key = RELEASE_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, "1", TTL_HOURS, TimeUnit.HOURS);
    }

    @Override
    public Optional<String> getDeductOrderId(String idempotencyKey) {
        String key = DEDUCT_ORDER_PREFIX + idempotencyKey;
        String orderId = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(orderId);
    }

    @Override
    public boolean bindDeductOrderIdIfAbsent(String idempotencyKey, String orderId) {
        String key = DEDUCT_ORDER_PREFIX + idempotencyKey;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, orderId, TTL_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(success);
    }

    // ========================== Kafka 消费幂等（扣减） ==========================

    /**
     * 检查 Kafka 扣减消息是否已处理
     * <p>
     * Key 格式：idem:kafka:deduct:{orderId}
     * <p>
     * 用于防止 Kafka 消费者重复消费同一 orderId 的扣减事件。
     * 与领域服务内部的幂等（idem:deduct:{idempotencyKey}）独立，提供双重保障。
     *
     * @param orderId 订单 ID
     * @return true 如果已处理过
     */
    public boolean isKafkaDeductProcessed(String orderId) {
        String key = KAFKA_DEDUCT_PREFIX + orderId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记 Kafka 扣减消息已处理
     * <p>
     * 成功消费后调用，TTL 24h。
     *
     * @param orderId 订单 ID
     */
    public void markKafkaDeductProcessed(String orderId) {
        String key = KAFKA_DEDUCT_PREFIX + orderId;
        redisTemplate.opsForValue().set(key, "1", TTL_HOURS, TimeUnit.HOURS);
    }

    // ========================== Kafka 消费幂等（释放） ==========================

    /**
     * 检查 Kafka 释放消息是否已处理
     * <p>
     * Key 格式：idem:kafka:release:{orderId}
     *
     * @param orderId 订单 ID
     * @return true 如果已处理过
     */
    public boolean isKafkaReleaseProcessed(String orderId) {
        String key = KAFKA_RELEASE_PREFIX + orderId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记 Kafka 释放消息已处理
     *
     * @param orderId 订单 ID
     */
    public void markKafkaReleaseProcessed(String orderId) {
        String key = KAFKA_RELEASE_PREFIX + orderId;
        redisTemplate.opsForValue().set(key, "1", TTL_HOURS, TimeUnit.HOURS);
    }
}
