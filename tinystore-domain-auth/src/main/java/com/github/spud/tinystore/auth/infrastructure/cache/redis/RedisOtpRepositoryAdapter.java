package com.github.spud.tinystore.auth.infrastructure.cache.redis;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import com.github.spud.tinystore.auth.application.port.out.OtpConsumeResult;
import com.github.spud.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;

/**
 * Redis OTP 仓储（C4 修复）。
 *
 * <p>验证码存放在共享 Redis 里，因此任一副本签发的验证码都能被其它副本校验；
 * 校验+消费由一段 Lua 原子完成，保证同一验证码最多被成功消费一次。
 *
 * <p>仅 {@code local}/{@code test} 之外的 profile 生效——内存实现是"每 JVM 一份"，
 * 多副本下会导致约 (N-1)/N 的登录失败。
 */
@Component
public class RedisOtpRepositoryAdapter implements OtpRepositoryPort {

    private static final String KEY_PREFIX = "auth:otp:";
    /** 过期后仍保留一小段时间，便于把 EXPIRED 与 NOT_FOUND 区分开。 */
    private static final Duration EXPIRY_GRACE = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> consumeScript;

    public RedisOtpRepositoryAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>();
        this.consumeScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/auth_otp_consume.lua")));
        this.consumeScript.setResultType(String.class);
    }

    @Override
    public Otp save(Otp otp) {
        String key = key(otp.getPhone());
        // 用带 TTL 的 SET 写入（而不是先写后 expire）：auth 的 Redis 连接由 Redisson 提供，
        // 其 pExpire 会自我递归导致 StackOverflowError，而 set(key,value,ttl) 不经过该路径。
        Duration ttl = Duration.between(OffsetDateTime.now(), otp.getExpireAt()).plus(EXPIRY_GRACE);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = EXPIRY_GRACE;
        }
        redisTemplate.opsForValue().set(key, otp.getCode().value() + "|"
                + otp.getExpireAt().toInstant().toEpochMilli(), ttl);
        return otp;
    }

    @Override
    public Optional<Otp> findLatest(PhoneNumber phone) {
        String raw = redisTemplate.opsForValue().get(key(phone));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        int sep = raw.lastIndexOf('|');
        String codeValue = sep < 0 ? raw : raw.substring(0, sep);
        long expireAtMs = sep < 0 ? 0L : Long.parseLong(raw.substring(sep + 1));
        return Optional.of(new Otp(
                phone.value(),
                phone,
                OtpCode.of(codeValue),
                OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(expireAtMs),
                        java.time.ZoneOffset.UTC),
                false,
                null));
    }

    @Override
    public void markUsed(Otp otp) {
        redisTemplate.delete(key(otp.getPhone()));
    }

    @Override
    public OtpConsumeResult verifyAndConsume(PhoneNumber phone, OtpCode code) {
        String result = redisTemplate.execute(consumeScript, List.of(key(phone)),
                code.value(), String.valueOf(System.currentTimeMillis()));
        if (result == null) {
            return OtpConsumeResult.NOT_FOUND;
        }
        return switch (result) {
            case "SUCCESS" -> OtpConsumeResult.SUCCESS;
            case "EXPIRED" -> OtpConsumeResult.EXPIRED;
            case "MISMATCH" -> OtpConsumeResult.MISMATCH;
            default -> OtpConsumeResult.NOT_FOUND;
        };
    }

    private String key(PhoneNumber phone) {
        return KEY_PREFIX + phone.value();
    }
}
