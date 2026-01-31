package com.github.spud.tinystore.account.infrastructure.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CredentialVersionStore {

    private static final String KEY_PREFIX = "tinystore:security:credential-version:";
    private static final Duration TTL = Duration.ofDays(365);

    private final StringRedisTemplate stringRedisTemplate;

    public CredentialVersionStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void save(String userId, Long version) {
        if (userId == null || version == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, String.valueOf(version), TTL);
    }
}

