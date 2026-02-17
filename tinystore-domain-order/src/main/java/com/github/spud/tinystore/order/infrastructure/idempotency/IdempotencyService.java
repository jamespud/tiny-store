package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 幂等服务（Redis SETNX 实现）
 *
 * Redis 故障处理策略：
 * - 当 fail-on-redis-error=true 时（默认），Redis 异常时抛出 IdempotencyServiceUnavailableException（返回 503）
 * - 当 fail-on-redis-error=false 时（降级模式），Redis 异常时允许请求通过（跳过幂等检查，有重复风险）
 */
@Slf4j
@Service
public class IdempotencyService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${order.idempotency.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${order.idempotency.fail-on-redis-error:true}")
    private boolean failOnRedisError;

    /**
     * 尝试获取幂等锁
     *
     * @param scope 作用域（例如"order:create"）
     * @param idempotencyKey 幂等键（例如 UUID）
     * @param fingerprint 请求指纹（用于验证重复请求的一致性）
     * @return true 获取成功（首次请求），false 获取失败（重复请求）
     * @throws IdempotencyServiceUnavailableException 当 Redis 不可用且 fail-on-redis-error=true 时
     */
    public boolean tryAcquire(String scope, String idempotencyKey, String fingerprint) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        try {
            Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, fingerprint, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return handleRedisException("tryAcquire", idempotencyKey, e, true);
        } catch (Exception e) {
            log.error("[operation=tryAcquire] [idempotencyKey={}] Unexpected exception during Redis operation",
                    idempotencyKey, e);
            return handleRedisException("tryAcquire", idempotencyKey, e, true);
        }
    }

    /**
     * 获取缓存的响应（用于重复请求时返回上一次的结果）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     * @return 缓存的响应内容，若不存在则返回 null
     * @throws IdempotencyServiceUnavailableException 当 Redis 不可用且 fail-on-redis-error=true 时
     */
    public String getCachedResponse(String scope, String idempotencyKey) {
        String responseKey = buildResponseRedisKey(scope, idempotencyKey);
        try {
            return redisTemplate.opsForValue().get(responseKey);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return handleRedisException("getCachedResponse", idempotencyKey, e, null);
        } catch (Exception e) {
            log.error("[operation=getCachedResponse] [idempotencyKey={}] Unexpected exception during Redis operation",
                    idempotencyKey, e);
            return handleRedisException("getCachedResponse", idempotencyKey, e, null);
        }
    }

    /**
     * 缓存响应（在幂等锁获取成功后，业务处理完成后调用）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     * @param responseJson 响应 JSON 序列化字符串
     * @throws IdempotencyServiceUnavailableException 当 Redis 不可用且 fail-on-redis-error=true 时
     */
    public void storeResponse(String scope, String idempotencyKey, String responseJson) {
        String responseKey = buildResponseRedisKey(scope, idempotencyKey);
        try {
            redisTemplate.opsForValue()
                .set(responseKey, responseJson, ttlSeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            handleRedisException("storeResponse", idempotencyKey, e, null);
        } catch (Exception e) {
            log.error("[operation=storeResponse] [idempotencyKey={}] Unexpected exception during Redis operation",
                    idempotencyKey, e);
            handleRedisException("storeResponse", idempotencyKey, e, null);
        }
    }

    /**
     * 清除幂等缓存（调试/管理用）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     */
    public void releaseLock(String scope, String idempotencyKey) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        String responseKey = buildResponseRedisKey(scope, idempotencyKey);
        redisTemplate.delete(redisKey);
        redisTemplate.delete(responseKey);
    }

    private String buildRedisKey(String scope, String idempotencyKey) {
        return "idempotency:" + scope + ":" + idempotencyKey;
    }

    private String buildResponseRedisKey(String scope, String idempotencyKey) {
        return "idempotency:response:" + scope + ":" + idempotencyKey;
    }

    /**
     * 处理 Redis 异常
     *
     * @param operation 操作名称
     * @param idempotencyKey 幂等键
     * @param cause 原始异常
     * @param fallbackValue 降级模式下的返回值
     * @param <T> 返回类型
     * @return 降级模式下返回 fallbackValue，否则抛出异常
     * @throws IdempotencyServiceUnavailableException 当 fail-on-redis-error=true 时
     */
    private <T> T handleRedisException(String operation, String idempotencyKey, Throwable cause, T fallbackValue) {
        if (failOnRedisError) {
            log.error("[operation={}] [idempotencyKey={}] Idempotency service unavailable: Redis error",
                    operation, idempotencyKey, cause);
            throw new IdempotencyServiceUnavailableException(operation, idempotencyKey, cause);
        } else {
            log.warn("[operation={}] [idempotencyKey={}] Redis unavailable, using fallback value (degraded mode): {}",
                    operation, idempotencyKey, fallbackValue);
            return fallbackValue;
        }
    }
}
