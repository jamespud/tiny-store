package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;

/**
 * Task 7 / C12 safety net 回归：孤儿预扣的<b>定向</b>回收。
 *
 * <p>背景：<code>inventory:uncommit</code> ZSET 的 member 形如
 * {@code <orderId>_<timestamp>_<amount>}，它同时就是 reservation_id。下单事务提交后靠 outbox
 * (Kafka) 异步建立 DB 预约行（{@code INVENTORY_RESERVE_DB}）。若下单事务回滚、或 JVM 在补偿前崩溃，
 * 这个预扣就变成<b>孤儿</b>：Redis 已记账但 DB 永远不会出现对应预约。
 *
 * <p>本测试用真实 PostgreSQL + Redis 断言两件事：
 * <ol>
 *   <li>孤儿（age 超阈值、DB 无行）被定向回收，库存恢复可售；</li>
 *   <li>仍然合法的预约（age 同样超阈值、但 DB 有行）<b>绝不被提前释放</b>。</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = InventoryApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "inventory.reconciliation.enabled=false",
        "inventory.reservation.expiry.enabled=false",
        "inventory.uncommit.orphan-check-delay=PT10M",
        "inventory.uncommit.timeout=PT30M"
    }
)
@DisplayName("inventory uncommit orphan reclaim (C12)")
@SuppressWarnings("resource")
class InventoryUncommitOrphanReclaimIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
        .withDatabaseName("tinystore")
        .withUsername("postgres")
        .withPassword("postgres")
        .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    private static final String SHOP = "shop-orphan-it";
    private static final String SKU = "sku-orphan-it";
    private static final String TOTAL_KEY = "inventory:total:" + SHOP + ":" + SKU;
    private static final String DEDUCTED_KEY = "inventory:deducted:" + SHOP + ":" + SKU;
    private static final String VERSION_KEY = "inventory:version:" + SHOP + ":" + SKU;
    private static final String UNCOMMIT_KEY = "inventory:uncommit:" + SHOP + ":" + SKU;

    /** 远超 orphan-check-delay(PT10M) 的入队时间，确保进入候选集。 */
    private static long staleScore() {
        return System.currentTimeMillis() - Duration.ofMinutes(20).toMillis();
    }

    @Autowired
    private InventoryUncommitV2CleanupTask cleanupTask;

    @Autowired
    private InventoryRedisManager redisManager;

    @Autowired
    private StringRedisTemplate stringRedis;

    @Autowired
    private JpaInventoryReservationRepository reservationRepository;

    @BeforeEach
    void resetState() {
        stringRedis.delete(List.of(TOTAL_KEY, DEDUCTED_KEY, VERSION_KEY, UNCOMMIT_KEY));
        stringRedis.opsForValue().set(TOTAL_KEY, "100");
        stringRedis.opsForValue().set(VERSION_KEY, "0");
        reservationRepository.deleteAll();
    }

    @Test
    @DisplayName("孤儿预扣（DB 无预约行）在阈值后被定向回收，库存恢复可售")
    void orphanPreDeduction_isReclaimedAndStockBecomesSellableAgain() {
        // 预扣 3 件但 DB 无对应预约：模拟下单事务回滚 / JVM 崩溃后补偿未执行
        String orphanMember = "orphan-order-" + UUID.randomUUID() + "_" + staleScore() + "_3";
        stringRedis.opsForValue().set(DEDUCTED_KEY, "3");
        stringRedis.opsForZSet().add(UNCOMMIT_KEY, orphanMember, staleScore());

        cleanupTask.cleanupOnce();

        // member 被回收，deducted 归零
        assertThat(stringRedis.opsForZSet().score(UNCOMMIT_KEY, orphanMember)).isNull();
        assertThat(stringRedis.opsForValue().get(DEDUCTED_KEY)).isEqualTo("0");

        // 库存恢复可售：同样的 3 件现在可以再次预扣成功
        String nextMember = redisManager.preDeductInventoryV2(SHOP, SKU, 3, "order-after-reclaim");
        assertThat(nextMember).isNotNull();
        assertThat(stringRedis.opsForValue().get(DEDUCTED_KEY)).isEqualTo("3");
    }

    @Test
    @DisplayName("合法预约（DB 有预约行）即使 age 超阈值也绝不被提前释放")
    void legitimateReservation_isNeverReleasedEarly() {
        String legitMember = "legit-order-" + UUID.randomUUID() + "_" + staleScore() + "_2";
        stringRedis.opsForValue().set(DEDUCTED_KEY, "2");
        stringRedis.opsForZSet().add(UNCOMMIT_KEY, legitMember, staleScore());

        // DB 权威预约行存在（reservation_id == uncommit member）
        reservationRepository.save(new InventoryReservationEntity()
                .setReservationId(legitMember)
                .setShopId(SHOP)
                .setSkuId(SKU)
                .setQuantity(2)
                .setStatus("PRE_DEDUCTED")
                .setTradeId("trade-" + UUID.randomUUID())
                .setOperationId("op-" + UUID.randomUUID())
                .setExpireAt(OffsetDateTime.now().plusMinutes(15)));

        cleanupTask.cleanupOnce();

        // 合法预扣既没被回收，deducted 也没被扣减
        assertThat(stringRedis.opsForZSet().score(UNCOMMIT_KEY, legitMember)).isNotNull();
        assertThat(stringRedis.opsForValue().get(DEDUCTED_KEY)).isEqualTo("2");
    }
}
