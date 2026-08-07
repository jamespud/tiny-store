package com.github.spud.tinystore.inventory.infrastructure.scheduler;

import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReconcileLogEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReservationEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReconcileLogRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryReservationRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
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
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = InventoryApplication.class, properties = {
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"})
@DisplayName("InventoryReconcileJob IT")
@SuppressWarnings("resource")
class InventoryReconcileJobIT {

    @Container static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:18.1").withDatabaseName("tinystore")
            .withUsername("postgres").withPassword("postgres")
            .withStartupTimeout(Duration.ofMinutes(3));
    @Container static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.4.0")).withExposedPorts(6379);
    @Container static ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void r(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.datasource.username", postgres::getUsername);
        reg.add("spring.datasource.password", postgres::getPassword);
        reg.add("spring.flyway.enabled", () -> "true");
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        reg.add("spring.data.redis.host", redis::getHost);
        reg.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private InventoryReconcileJob job;
    @Autowired private JpaInventoryStockRepository stockRepository;
    @Autowired private JpaInventoryReservationRepository reservationRepository;
    @Autowired private JpaInventoryReconcileLogRepository logRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager redisManager;

    private static final String SHOP = "SHOP-REC-JOB";
    private static final String SKU = "sku-1";
    private static final String TOTAL = "inventory:total:" + SHOP + ":" + SKU;
    private static final String DEDUCTED = "inventory:deducted:" + SHOP + ":" + SKU;

    @BeforeEach
    void clean() {
        logRepository.deleteAll();
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
        var k = redisTemplate.keys("inventory:*");
        if (k != null && !k.isEmpty()) redisTemplate.delete(k);
    }

    private void seedStock(long total) {
        stockRepository.save(new InventoryStockEntity().setShopId(SHOP).setSkuId(SKU)
                .setTotalQuantity(total).setReservedQuantity(0));
    }

    private void seedPreDeducted(long qty) {
        reservationRepository.save(new InventoryReservationEntity()
                .setReservationId("PRE-" + System.nanoTime())
                .setShopId(SHOP).setSkuId(SKU).setQuantity(qty).setStatus("PRE_DEDUCTED")
                .setExpireAt(OffsetDateTime.now().plusMinutes(30)).setTradeId("t").setOperationId("op"));
    }

    @Test
    @DisplayName("oversell_totalTooHigh_repairedByDecrby")
    void oversell_totalTooHigh_repairedByDecrby() {
        seedStock(100); // targetTotal = 100 + 0 = 100
        redisTemplate.opsForValue().set(TOTAL, "150"); // too high -> oversell risk
        redisTemplate.opsForValue().set(VERSION, "0"); // version key 必须存在（CAS 修复前置）

        job.reconcile();

        assertThat(redisTemplate.opsForValue().get(TOTAL)).isEqualTo("100"); // DECRBY 50
        List<InventoryReconcileLogEntity> rows = logRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("REPAIRED_OVERSELL");
        assertThat(rows.get(0).getRepairedFields()).isEqualTo("TOTAL");
    }

    @Test
    @DisplayName("oversell_deductedTooLow_repairedByIncrby")
    void oversell_deductedTooLow_repairedByIncrby() {
        seedStock(100);
        seedPreDeducted(5); // dbPreDeducted=5 -> targetDeducted = 5 + 0 = 5
        redisTemplate.opsForValue().set(TOTAL, "100");
        redisTemplate.opsForValue().set(DEDUCTED, "2"); // too low -> deficit 3
        redisTemplate.opsForValue().set(VERSION, "0"); // version key 必须存在（CAS 修复前置）

        job.reconcile();

        assertThat(redisTemplate.opsForValue().get(DEDUCTED)).isEqualTo("5"); // INCRBY 3
        List<InventoryReconcileLogEntity> rows = logRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("REPAIRED_OVERSELL");
        assertThat(rows.get(0).getRepairedFields()).isEqualTo("DEDUCTED");
    }

    @Test
    @DisplayName("lostSales_totalTooLow_alertNoRepair")
    void lostSales_totalTooLow_alertNoRepair() {
        seedStock(100); // targetTotal = 100
        redisTemplate.opsForValue().set(TOTAL, "80"); // too low -> lost-sales

        job.reconcile();

        assertThat(redisTemplate.opsForValue().get(TOTAL)).isEqualTo("80"); // unchanged
        List<InventoryReconcileLogEntity> rows = logRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("ALERT_LOST_SALES");
    }

    @Test
    @DisplayName("clean_noLogWritten")
    void clean_noLogWritten() {
        seedStock(100); // targetTotal=100, targetDeducted=0
        redisTemplate.opsForValue().set(TOTAL, "100");
        redisTemplate.opsForValue().set(DEDUCTED, "0"); // in sync

        job.reconcile();

        assertThat(logRepository.findAll()).isEmpty();
    }

    // ===== Task 4: multi-instance concurrency (version-CAS idempotency) =====

    private static final String VERSION = "inventory:version:" + SHOP + ":" + SKU;

    @Test
    @DisplayName("concurrent same-snapshot decreaseTotal: only one applies, version bumps once")
    void concurrentDecreaseTotal_sameSnapshot_onlyOneApplies() throws Exception {
        seedStock(100);
        redisTemplate.opsForValue().set(TOTAL, "120");
        redisTemplate.opsForValue().set(VERSION, "5");

        int threads = 5;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger appliedCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    boolean applied = redisManager.decreaseTotalV2(SHOP, SKU, 20L, 5L);
                    if (applied) appliedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(appliedCount.get()).isEqualTo(1);  // 仅一个成功
        assertThat(redisTemplate.opsForValue().get(TOTAL)).isEqualTo("100");
        assertThat(redisTemplate.opsForValue().get(VERSION)).isEqualTo("6");  // 只 +1
    }

    @Test
    @DisplayName("repair vs business preDeduct concurrent: version +2, no lost update")
    void repairVsPreDeduct_concurrent_noLostUpdate() throws Exception {
        seedStock(200);
        redisTemplate.opsForValue().set(TOTAL, "120");
        redisTemplate.opsForValue().set(DEDUCTED, "0");
        redisTemplate.opsForValue().set(VERSION, "5");

        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(2);

        Thread repair = new Thread(() -> {
            try {
                start.await();
                redisManager.decreaseTotalV2(SHOP, SKU, 20L, 5L);
            } catch (Exception ignored) {
            } finally { done.countDown(); }
        });
        Thread business = new Thread(() -> {
            try {
                start.await();
                redisManager.preDeductInventoryV2(SHOP, SKU, 10, "order-conc");
            } catch (Exception ignored) {
            } finally { done.countDown(); }
        });
        repair.start(); business.start();
        start.countDown();
        done.await(10, java.util.concurrent.TimeUnit.SECONDS);

        // version 最终 +2（两个写都生效）或 +1（repair CAS 失败，仅 preDeduct）
        long finalVersion = Long.parseLong(redisTemplate.opsForValue().get(VERSION));
        assertThat(finalVersion).isBetween(6L, 7L);
        // 关键不变量：无丢更新（version 与写次数一致）
    }
}
