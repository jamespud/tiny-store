package com.github.spud.tinystore.inventory.infrastructure.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * InventoryRedisManager Lua 行为测试（Testcontainers Redis）。
 * 覆盖 version-key 不变量：每次写 total/deducted 必须原子 bump version。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("InventoryRedisManager version-bump tests")
class InventoryRedisManagerTest {

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private StringRedisTemplate redisTemplate;
    private InventoryRedisManager redisManager;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        redisManager = new InventoryRedisManager();
        ReflectionTestUtils.setField(redisManager, "redisTemplate", redisTemplate);
        redisManager.initRedisTemplate();

        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    private void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    private String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Test
    @DisplayName("addTotalV2 positive delta: INCRBY total + INCR version (atomic)")
    void addTotalV2_positiveDelta_shouldBumpVersion() {
        set("inventory:total:shop:sku", "100");
        set("inventory:version:shop:sku", "5");

        boolean ok = redisManager.addTotalV2("shop", "sku", 10L);

        assertThat(ok).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("110");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("addTotalV2 negative delta: DECRBY total + INCR version (atomic)")
    void addTotalV2_negativeDelta_shouldBumpVersion() {
        set("inventory:total:shop:sku", "100");
        set("inventory:version:shop:sku", "5");

        redisManager.addTotalV2("shop", "sku", -30L);

        assertThat(get("inventory:total:shop:sku")).isEqualTo("70");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }
}
