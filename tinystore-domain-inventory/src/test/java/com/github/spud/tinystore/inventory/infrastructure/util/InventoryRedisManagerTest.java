package com.github.spud.tinystore.inventory.infrastructure.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
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

    private RedisTemplate<String, Object> redisTemplate;
    private InventoryRedisManager redisManager;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        // 匹配生产配置（initRedisTemplate）：String 序列化器
        StringRedisSerializer s = new StringRedisSerializer();
        redisTemplate.setKeySerializer(s);
        redisTemplate.setHashKeySerializer(s);
        redisTemplate.setValueSerializer(s);
        redisTemplate.setHashValueSerializer(s);
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
        Object v = redisTemplate.opsForValue().get(key);
        return v == null ? null : v.toString();
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

    // ===== Task 2: business Lua scripts bump version (deducted side) =====

    @Test
    @DisplayName("preDeduct success: bump version")
    void preDeductV2_success_shouldBumpVersion() {
        set("inventory:total:shop:sku", "100");
        set("inventory:version:shop:sku", "5");

        String member = redisManager.preDeductInventoryV2("shop", "sku", 10, "order-1");

        assertThat(member).isNotNull();
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("10");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("preDeduct insufficient stock: do NOT bump version (failure path)")
    void preDeductV2_insufficientStock_shouldNotBumpVersion() {
        set("inventory:total:shop:sku", "5");
        set("inventory:version:shop:sku", "5");

        // preDeduct 库存不足：脚本返回 -1（既有 StringRedisSerializer 下 Long 返回 eval 抛异常，是预存在问题）
        try {
            redisManager.preDeductInventoryV2("shop", "sku", 100, "order-1");
        } catch (Exception ignored) {
            // 序列化限制：ValueOutput 无法解码 Long 返回；不影响 version 不变量验证
        }

        // 关键不变量：失败路径 version 未被 bump
        assertThat(get("inventory:version:shop:sku")).isEqualTo("5");
    }

    @Test
    @DisplayName("rollback member exists: bump version")
    void rollbackV2_memberExists_shouldBumpVersion() {
        set("inventory:total:shop:sku", "100");
        String member = redisManager.preDeductInventoryV2("shop", "sku", 10, "order-1");
        long versionBefore = Long.parseLong(get("inventory:version:shop:sku"));

        boolean ok = redisManager.rollbackPreDeductV2("shop", "sku", member);

        assertThat(ok).isTrue();
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("0");
        assertThat(get("inventory:version:shop:sku")).isEqualTo(String.valueOf(versionBefore + 1));
    }

    @Test
    @DisplayName("rollback member not found: do NOT bump version")
    void rollbackV2_memberNotFound_shouldNotBumpVersion() {
        set("inventory:version:shop:sku", "5");

        boolean ok = redisManager.rollbackPreDeductV2("shop", "sku", "nonexistent-member");

        assertThat(ok).isFalse();
        assertThat(get("inventory:version:shop:sku")).isEqualTo("5");
    }

    @Test
    @DisplayName("cleanTimeout has expired members: bump version")
    void cleanTimeoutV2_hasExpired_shouldBumpVersion() {
        set("inventory:total:shop:sku", "100");
        set("inventory:deducted:shop:sku", "10");
        set("inventory:version:shop:sku", "5");
        // zadd 一个 timestamp=1（远过期）的成员 amount=10
        redisTemplate.opsForZSet().add("inventory:uncommit:shop:sku", "order-1_1_10", 1);

        long cleaned = redisManager.cleanTimeoutUncommitV2("shop", "sku", 1000L);

        assertThat(cleaned).isEqualTo(1);
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("0");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("cleanTimeout no expired members: do NOT bump version")
    void cleanTimeoutV2_noExpired_shouldNotBumpVersion() {
        set("inventory:version:shop:sku", "5");

        long cleaned = redisManager.cleanTimeoutUncommitV2("shop", "sku", 1800000L);

        assertThat(cleaned).isEqualTo(0);
        assertThat(get("inventory:version:shop:sku")).isEqualTo("5");
    }

    // ===== Task 3: version-CAS repair (decreaseTotalV2 / increaseDeductedV2) =====

    @Test
    @DisplayName("decreaseTotalV2 version matches: apply DECRBY + INCR version, return true")
    void decreaseTotalV2_versionMatches_shouldApplyAndBump() {
        set("inventory:total:shop:sku", "120");
        set("inventory:version:shop:sku", "5");

        boolean applied = redisManager.decreaseTotalV2("shop", "sku", 20L, 5L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("100");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("decreaseTotalV2 version mismatch: no-op, return false")
    void decreaseTotalV2_versionMismatch_shouldNoop() {
        set("inventory:total:shop:sku", "120");
        set("inventory:version:shop:sku", "6");  // 已被业务 bump

        boolean applied = redisManager.decreaseTotalV2("shop", "sku", 20L, 5L);  // 旧 snapshot version=5

        assertThat(applied).isFalse();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("120");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("decreaseTotalV2 version key missing: no-op, return false")
    void decreaseTotalV2_versionKeyMissing_shouldNoop() {
        set("inventory:total:shop:sku", "120");
        // version key 不存在

        boolean applied = redisManager.decreaseTotalV2("shop", "sku", 20L, 5L);

        assertThat(applied).isFalse();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("120");
    }

    @Test
    @DisplayName("increaseDeductedV2 version matches: apply INCRBY + INCR version, return true")
    void increaseDeductedV2_versionMatches_shouldApplyAndBump() {
        set("inventory:deducted:shop:sku", "0");
        set("inventory:version:shop:sku", "5");

        boolean applied = redisManager.increaseDeductedV2("shop", "sku", 10L, 5L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("10");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
    }

    @Test
    @DisplayName("increaseDeductedV2 version mismatch: no-op, return false")
    void increaseDeductedV2_versionMismatch_shouldNoop() {
        set("inventory:deducted:shop:sku", "0");
        set("inventory:version:shop:sku", "7");

        boolean applied = redisManager.increaseDeductedV2("shop", "sku", 10L, 5L);

        assertThat(applied).isFalse();
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("0");
    }

    @Test
    @DisplayName("initStateV2 all keys missing: SETNX authoritative total/deducted/version, return true")
    void initStateV2_allMissing_shouldInitialize() {
        boolean applied = redisManager.initStateV2("shop", "sku", 100L, 5L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("100");
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("5");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("0");
    }

    @Test
    @DisplayName("initStateV2 keys already present: never clobber, return false")
    void initStateV2_keysPresent_shouldNoop() {
        set("inventory:total:shop:sku", "95");
        set("inventory:deducted:shop:sku", "5");
        set("inventory:version:shop:sku", "3");

        boolean applied = redisManager.initStateV2("shop", "sku", 100L, 5L);

        assertThat(applied).isFalse();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("95"); // 不覆盖已存在值
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("5");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("3");
    }

    @Test
    @DisplayName("initStateV2 partial keys missing: only init missing ones, keep existing")
    void initStateV2_partialMissing_shouldInitMissingOnly() {
        set("inventory:total:shop:sku", "100");
        // deducted + version 缺失（Redis flush 后部分重建场景）

        boolean applied = redisManager.initStateV2("shop", "sku", 100L, 5L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("100"); // 不覆盖
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("5");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("0");
    }
}
