package com.github.spud.tinystore.order.infrastructure.idempotency;

import com.github.spud.tinystore.order.domain.exception.IdempotencyServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;
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

    private final MeterRegistry meterRegistry;
    private final Counter idempotencyUnavailableCounter;

    @Value("${order.idempotency.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${order.idempotency.fail-on-redis-error:true}")
    private boolean failOnRedisError;

    private final DefaultRedisScript<String> acquireScript;
    private final DefaultRedisScript<Long> succeedScript;

    public IdempotencyService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.idempotencyUnavailableCounter = Counter.builder("tinystore.idempotency.unavailable.total")
            .description("Count of idempotency service unavailable exceptions")
            .tag("service", "order")
            .register(meterRegistry);
        this.acquireScript = new DefaultRedisScript<>();
        this.acquireScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("scripts/idempotency_acquire.lua")));
        this.acquireScript.setResultType(String.class);
        this.succeedScript = new DefaultRedisScript<>();
        this.succeedScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("scripts/idempotency_succeed.lua")));
        this.succeedScript.setResultType(Long.class);
    }

    /**
     * 幂等获取结果。
     */
    public enum AcquireResult {
        /** 首次请求，调用方获得处理权，必须继续执行业务事务。 */
        ACQUIRED,
        /** 同键同指纹的请求正在处理中；调用方应返回冲突，让客户端稍后重试。 */
        IN_PROGRESS,
        /** 同键同指纹且上一次已成功；调用方应直接重放缓存响应。 */
        REPLAY,
        /** 同键但请求体不同；调用方必须拒绝，绝不能返回上一次的结果。 */
        FINGERPRINT_CONFLICT
    }

    /**
     * 原子获取幂等权（fingerprint 比对与状态写入在同一次 Redis 操作内完成）。
     *
     * @param scope 作用域，例如 {@code trade:create}
     * @param idempotencyKey 幂等键
     * @param fingerprint 请求指纹，见 {@code TradeRequestFingerprint}
     * @return 见 {@link AcquireResult}
     * @throws IdempotencyServiceUnavailableException Redis 不可用且 fail-on-redis-error=true
     */
    public AcquireResult acquire(String scope, String idempotencyKey, String fingerprint) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        try {
            String result = redisTemplate.execute(acquireScript, List.of(redisKey),
                fingerprint, String.valueOf(ttlSeconds));
            return parseAcquireResult(result);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return degradedAcquire(handleRedisException("acquire", idempotencyKey, e, null));
        } catch (Exception e) {
            log.error("[operation=acquire] [idempotencyKey={}] Unexpected exception during Redis operation",
                idempotencyKey, e);
            return degradedAcquire(handleRedisException("acquire", idempotencyKey, e, null));
        }
    }

    private AcquireResult parseAcquireResult(String raw) {
        if (raw == null) {
            // 脚本没有返回状态：保守起见当作"未取得"，让调用方走冲突分支。
            return AcquireResult.IN_PROGRESS;
        }
        return switch (raw) {
            case "ACQUIRED" -> AcquireResult.ACQUIRED;
            case "REPLAY" -> AcquireResult.REPLAY;
            case "FINGERPRINT_CONFLICT" -> AcquireResult.FINGERPRINT_CONFLICT;
            default -> AcquireResult.IN_PROGRESS;
        };
    }

    private AcquireResult degradedAcquire(Object fallback) {
        // 降级模式（fail-on-redis-error=false）下 fallback 为 null，视为放行。
        return AcquireResult.ACQUIRED;
    }

    /**
     * 读取上一次成功请求缓存的响应（仅在 {@link AcquireResult#REPLAY} 时使用）。
     */
    public String getStoredResponse(String scope, String idempotencyKey) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        try {
            Object value = redisTemplate.opsForHash().get(redisKey, "response");
            return value == null ? null : value.toString();
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return handleRedisException("getStoredResponse", idempotencyKey, e, null);
        } catch (Exception e) {
            log.error("[operation=getStoredResponse] [idempotencyKey={}] Unexpected exception",
                idempotencyKey, e);
            return handleRedisException("getStoredResponse", idempotencyKey, e, null);
        }
    }

    /**
     * 业务事务提交之后把幂等记录推进为 SUCCEEDED 并缓存响应。
     *
     * <p>必须在 afterCommit 调用：在提交前写入 SUCCEEDED 会让"事务回滚但客户端拿到成功"成为可能。
     */
    public void markSucceeded(String scope, String idempotencyKey, String fingerprint, String responseJson) {
        String redisKey = buildRedisKey(scope, idempotencyKey);
        try {
            redisTemplate.execute(succeedScript, List.of(redisKey),
                responseJson, String.valueOf(ttlSeconds), fingerprint);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            handleRedisException("markSucceeded", idempotencyKey, e, null);
        } catch (Exception e) {
            log.error("[operation=markSucceeded] [idempotencyKey={}] Unexpected exception", idempotencyKey, e);
            handleRedisException("markSucceeded", idempotencyKey, e, null);
        }
    }

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
            // 增加指标计数
            idempotencyUnavailableCounter.increment();
            throw new IdempotencyServiceUnavailableException(operation, idempotencyKey, cause);
        } else {
            log.warn("[operation={}] [idempotencyKey={}] Redis unavailable, using fallback value (degraded mode): {}",
                    operation, idempotencyKey, fallbackValue);
            return fallbackValue;
        }
    }
}
