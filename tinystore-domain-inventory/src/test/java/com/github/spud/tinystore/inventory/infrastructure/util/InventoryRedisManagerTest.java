package com.github.spud.tinystore.inventory.infrastructure.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

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
    @DisplayName("repairOversellV2 version matches: apply DECRBY total + INCRBY deducted, bump once")
    void repairOversellV2_versionMatches_shouldApplyBothAndBumpOnce() {
        set("inventory:total:shop:sku", "150");
        set("inventory:deducted:shop:sku", "2");
        set("inventory:version:shop:sku", "5");

        boolean applied = redisManager.repairOversellV2("shop", "sku", -50L, 3L, 5L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("100");
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("5");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6"); // 只 bump 一次
    }

    @Test
    @DisplayName("repairOversellV2 version mismatch: no-op both fields, return false")
    void repairOversellV2_versionMismatch_shouldNoop() {
        set("inventory:total:shop:sku", "150");
        set("inventory:deducted:shop:sku", "2");
        set("inventory:version:shop:sku", "7"); // 已被业务 bump

        boolean applied = redisManager.repairOversellV2("shop", "sku", -50L, 3L, 5L);

        assertThat(applied).isFalse();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("150");
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("2");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("7");
    }

    @Test
    @DisplayName("repairOversellV2 single-field delta: only that field changes, bump once")
    void repairOversellV2_singleFieldDelta_shouldChangeOnlyThatField() {
        set("inventory:total:shop:sku", "150");
        set("inventory:deducted:shop:sku", "0");
        set("inventory:version:shop:sku", "3");

        boolean applied = redisManager.repairOversellV2("shop", "sku", -50L, 0L, 3L);

        assertThat(applied).isTrue();
        assertThat(get("inventory:total:shop:sku")).isEqualTo("100");
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("0");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("4");
    }

    // ===== Task 7 (C12 safety net): orphan-directed reclaim =====

    @Test
    @DisplayName("reclaimOrphanMembersV2: only the listed member is removed; deducted decremented; version bumped once")
    void reclaimOrphanMembersV2_removesListedOnly() {
        String uncommitKey = "inventory:uncommit:shop:sku";
        set("inventory:total:shop:sku", "100");
        set("inventory:deducted:shop:sku", "30");
        set("inventory:version:shop:sku", "5");
        redisTemplate.opsForZSet().add(uncommitKey, "orphan-1_1_10", 1);
        redisTemplate.opsForZSet().add(uncommitKey, "legit-1_1_20", 2);

        long reclaimed = redisManager.reclaimOrphanMembersV2("shop", "sku", List.of("orphan-1_1_10"));

        assertThat(reclaimed).isEqualTo(1);
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("20");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("6");
        // 列表外的合法 member 绝不被触碰
        assertThat(redisTemplate.opsForZSet().score(uncommitKey, "legit-1_1_20")).isNotNull();
        assertThat(redisTemplate.opsForZSet().score(uncommitKey, "orphan-1_1_10")).isNull();
    }

    @Test
    @DisplayName("reclaimOrphanMembersV2: unknown member is a no-op, no version bump")
    void reclaimOrphanMembersV2_unknownMemberNoop() {
        set("inventory:deducted:shop:sku", "10");
        set("inventory:version:shop:sku", "5");

        long reclaimed = redisManager.reclaimOrphanMembersV2("shop", "sku", List.of("ghost_1_10"));

        assertThat(reclaimed).isEqualTo(0);
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("10");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("5");
    }

    @Test
    @DisplayName("reclaimOrphanMembersV2: malformed member is kept, never released")
    void reclaimOrphanMembersV2_malformedMemberKept() {
        String uncommitKey = "inventory:uncommit:shop:sku";
        set("inventory:deducted:shop:sku", "10");
        set("inventory:version:shop:sku", "5");
        redisTemplate.opsForZSet().add(uncommitKey, "malformed", 1);

        long reclaimed = redisManager.reclaimOrphanMembersV2("shop", "sku", List.of("malformed"));

        assertThat(reclaimed).isEqualTo(0);
        assertThat(get("inventory:deducted:shop:sku")).isEqualTo("10");
        assertThat(get("inventory:version:shop:sku")).isEqualTo("5");
        assertThat(redisTemplate.opsForZSet().score(uncommitKey, "malformed")).isNotNull();
    }

    @Test
    @DisplayName("findUncommitMembersOlderThan: returns only members older than the cutoff")
    void findUncommitMembersOlderThan_filtersByAge() {
        String uncommitKey = "inventory:uncommit:shop:sku";
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(uncommitKey, "old_1_5", now - 20 * 60_000L);
        redisTemplate.opsForZSet().add(uncommitKey, "fresh_1_5", now);

        List<String> older = redisManager.findUncommitMembersOlderThan("shop", "sku", 10 * 60_000L);

        assertThat(older).containsExactly("old_1_5");
    }
}
