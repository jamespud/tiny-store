package com.github.spud.tinystore.order.infrastructure.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 幂等服务（Redis SETNX 实现）
 */
@Slf4j
@Service
public class IdempotencyService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${order.idempotency.ttl-seconds:600}")
    private long ttlSeconds;

    /**
     * 尝试获取幂等锁
     *
     * @param scope 作用域（例如"order:create"）
     * @param idempotencyKey 幂等键（例如 UUID）
     * @param fingerprint 请求指纹（用于验证重复请求的一致性）
     * @return true 获取成功（首次请求），false 获取失败（重复请求）
     */
    public boolean tryAcquire(String scope, String idempotencyKey, String fingerprint) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(redisKey, fingerprint, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取缓存的响应（用于重复请求时返回上一次的结果）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     * @return 缓存的响应内容，若不存在则返回 null
     */
    public String getCachedResponse(String scope, String idempotencyKey) {
        String responseKey = buildResponseRedisKey(scope, idempotencyKey);
        return redisTemplate.opsForValue().get(responseKey);
    }

    /**
     * 缓存响应（在幂等锁获取成功后，业务处理完成后调用）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     * @param responseJson 响应 JSON 序列化字符串
     */
    public void storeResponse(String scope, String idempotencyKey, String responseJson) {
        String responseKey = buildResponseRedisKey(scope, idempotencyKey);
        redisTemplate.opsForValue()
            .set(responseKey, responseJson, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 清除幂等缓存（调试/管理用）
     *
     * @param scope 作用域
     * @param idempotencyKey 幂等键
     */
    public void removeCache(String scope, String idempotencyKey) {
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
}
