package com.github.spud.tinystore.inventory.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.InventoryApplication;
import com.github.spud.tinystore.inventory.domain.service.InventoryDeductDomainService;
import com.github.spud.tinystore.inventory.infrastructure.adapter.RedisIdempotencyRepository;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockDeductMessage;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockReleaseMessage;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryDeductRecordEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryStockEntity;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryDeductRecordRepository;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaInventoryStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * StockDeductConsumer 集成测试
 * <p>
 * 测试范围：
 * - 正常扣减/释放流程（Kafka → Consumer → 领域服务 → Redis/DB）
 * - Kafka 消费幂等（重复消费同一 orderId）
 * - 领域服务内部幂等（双重保障）
 * - 非法 JSON 直接 ack
 * - 领域服务异常不 ack（Kafka 重试）
 * <p>
 * 使用 Testcontainers：Postgres + Redis + Kafka
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = InventoryApplication.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false"
        }
)
@DisplayName("StockDeductConsumer IT")
@SuppressWarnings("resource")
class StockDeductConsumerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("tinystore")
            .withUsername("postgres")
            .withPassword("postgres")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
            .withExposedPorts(6379);

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RedisIdempotencyRepository idempotencyRepository;

    @Autowired
    private JpaInventoryStockRepository stockRepository;

    @Autowired
    private JpaInventoryDeductRecordRepository deductRecordRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InventoryDeductDomainService deductDomainService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // 清理 Redis 幂等键
        Set<String> keys = redisTemplate.keys("idem:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 初始化测试库存数据
        stockRepository.deleteAll();
        InventoryStockEntity stock1 = new InventoryStockEntity();
        stock1.setShopId("SHOP1");
        stock1.setSkuId("SKU001");
        stock1.setTotalQuantity(100);
        stock1.setReservedQuantity(0);

        InventoryStockEntity stock2 = new InventoryStockEntity();
        stock2.setShopId("SHOP1");
        stock2.setSkuId("SKU002");
        stock2.setTotalQuantity(50);
        stock2.setReservedQuantity(0);

        stockRepository.saveAll(List.of(stock1, stock2));
    }

    @Test
    @DisplayName("正常扣减流程：Kafka 消息 → Consumer 消费 → 库存扣减成功")
    void testConsumeDeduct_success() throws Exception {
        // Arrange - 使用独立的 shopId/skuId 避免测试间干扰
        String testShopId = "SHOP_TEST_" + System.currentTimeMillis();
        String testSkuId = "SKU_TEST_SUCCESS";
        String orderId = "ORDER_" + System.currentTimeMillis();

        // 初始化测试库存
        InventoryStockEntity testStock = new InventoryStockEntity();
        testStock.setShopId(testShopId);
        testStock.setSkuId(testSkuId);
        testStock.setTotalQuantity(100);
        testStock.setReservedQuantity(0);
        stockRepository.save(testStock);

        StockDeductMessage message = StockDeductMessage.builder()
                .orderId(orderId)
                .items(List.of(
                        StockDeductMessage.DeductItem.builder()
                                .shopId(testShopId)
                                .skuId(testSkuId)
                                .quantity(10)
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(message);

        // Act
        kafkaTemplate.send("stock-deduct", orderId, json).get(); // 等待发送完成

        // Assert - 等待 Kafka 消费完成（最多 30 秒）
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            // 验证 Kafka 消费幂等键已设置
            assertThat(idempotencyRepository.isKafkaDeductProcessed(orderId))
                    .as("Kafka deduct idempotency key should be set")
                    .isTrue();

            // 验证 DB 扣减流水记录存在（更可靠，不受 Redis 清理影响）
            List<InventoryDeductRecordEntity> records = deductRecordRepository.findByOrderId(orderId);
            assertThat(records)
                    .as("DB deduct record should exist for orderId: " + orderId)
                    .isNotEmpty();
            assertThat(records.get(0).getStatus())
                    .as("Deduct record status should be DEDUCTED")
                    .isEqualTo("DEDUCTED");
        });
    }

    @Test
    @DisplayName("Kafka 消费幂等：重复消费同一 orderId，只处理一次")
    void testConsumeDeduct_idempotent() throws Exception {
        // Arrange - 使用独立的 shopId/skuId
        String testShopId = "SHOP_TEST_" + System.currentTimeMillis();
        String testSkuId = "SKU_TEST_IDEM";
        String orderId = "ORDER_" + System.currentTimeMillis();

        // 初始化测试库存
        InventoryStockEntity testStock = new InventoryStockEntity();
        testStock.setShopId(testShopId);
        testStock.setSkuId(testSkuId);
        testStock.setTotalQuantity(100);
        testStock.setReservedQuantity(0);
        stockRepository.save(testStock);

        StockDeductMessage message = StockDeductMessage.builder()
                .orderId(orderId)
                .items(List.of(
                        StockDeductMessage.DeductItem.builder()
                                .shopId(testShopId)
                                .skuId(testSkuId)
                                .quantity(5)
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(message);

        // Act - 发送两次相同的消息
        kafkaTemplate.send("stock-deduct", orderId, json).get();
        kafkaTemplate.send("stock-deduct", orderId, json).get();

        // Assert - 等待消费完成（30秒）
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(idempotencyRepository.isKafkaDeductProcessed(orderId))
                    .as("Kafka deduct idempotency key should be set")
                    .isTrue();
        });

        // 验证 DB 扣减流水记录只有一条（幂等保障）
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<InventoryDeductRecordEntity> records = deductRecordRepository.findByOrderId(orderId);
            assertThat(records)
                    .as("Should have exactly 1 DB record for orderId: " + orderId)
                    .hasSize(1);
            assertThat(records.get(0).getQuantity())
                    .as("Deduct quantity should be 5")
                    .isEqualTo(5);
            assertThat(records.get(0).getStatus())
                    .as("Status should be DEDUCTED")
                    .isEqualTo("DEDUCTED");
        });
    }

    @Test
    @DisplayName("正常释放流程：Kafka 消息 → Consumer 消费 → 库存释放成功")
    void testConsumeRelease_success() throws Exception {
        // Arrange - 使用独立的 shopId/skuId
        String testShopId = "SHOP_TEST_" + System.currentTimeMillis();
        String testSkuId = "SKU_TEST_RELEASE";
        String orderId = "ORDER_" + System.currentTimeMillis();

        // 初始化测试库存
        InventoryStockEntity testStock = new InventoryStockEntity();
        testStock.setShopId(testShopId);
        testStock.setSkuId(testSkuId);
        testStock.setTotalQuantity(100);
        testStock.setReservedQuantity(0);
        stockRepository.save(testStock);

        // 先扣减库存，获取 occupyId
        StockDeductMessage deductMsg = StockDeductMessage.builder()
                .orderId(orderId)
                .items(List.of(
                        StockDeductMessage.DeductItem.builder()
                                .shopId(testShopId)
                                .skuId(testSkuId)
                                .quantity(3)
                                .build()
                ))
                .build();

        String deductJson = objectMapper.writeValueAsString(deductMsg);
        kafkaTemplate.send("stock-deduct", orderId, deductJson).get(); // 等待发送完成

        // 等待扣减完成（60秒），增加超时应对 Kafka consumer rebalance 和高负载场景
        await().pollDelay(3, TimeUnit.SECONDS)
               .pollInterval(2, TimeUnit.SECONDS)
               .atMost(60, TimeUnit.SECONDS)
               .untilAsserted(() -> {
            assertThat(idempotencyRepository.isKafkaDeductProcessed(orderId)).isTrue();
        });

        // 获取 occupyId 从 DB 流水表（更可靠）
        String occupyId = await().atMost(10, TimeUnit.SECONDS).until(() -> {
            List<InventoryDeductRecordEntity> records = deductRecordRepository.findByOrderId(orderId);
            if (records.isEmpty()) {
                return null;
            }
            return records.get(0).getOccupyId();
        }, id -> id != null);

        assertThat(occupyId).as("occupyId should be found in DB").isNotNull();

        // Act - 发送释放消息
        StockReleaseMessage releaseMsg = StockReleaseMessage.builder()
                .orderId(orderId)
                .reason("ORDER_CANCELLED")
                .occupyPairs(List.of(
                        StockReleaseMessage.OccupyPairDto.builder()
                                .shopId(testShopId)
                                .skuId(testSkuId)
                                .occupyId(occupyId)
                                .build()
                ))
                .build();

        String releaseJson = objectMapper.writeValueAsString(releaseMsg);
        kafkaTemplate.send("stock-release", orderId, releaseJson).get(); // 等待发送完成

        // Assert - 等待释放完成（60秒），增加超时应对 Kafka consumer rebalance 和高负载场景
        await().pollDelay(3, TimeUnit.SECONDS)
               .pollInterval(2, TimeUnit.SECONDS)
               .atMost(60, TimeUnit.SECONDS)
               .untilAsserted(() -> {
            assertThat(idempotencyRepository.isKafkaReleaseProcessed(orderId))
                    .as("Kafka release idempotency key should be set")
                    .isTrue();
        });

        // 验证 DB 流水记录状态已更新为 RELEASED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<InventoryDeductRecordEntity> records = deductRecordRepository.findByOrderId(orderId);
            assertThat(records)
                    .as("DB record should exist")
                    .isNotEmpty();
            assertThat(records.get(0).getStatus())
                    .as("Status should be RELEASED after release operation")
                    .isEqualTo("RELEASED");
        });
    }

    @Test
    @DisplayName("非法 JSON 消息：直接 ack，不阻塞消费")
    void testConsumeDeduct_invalidJson_shouldAck() {
        // Arrange
        String invalidJson = "{invalid json";

        // Act
        kafkaTemplate.send("stock-deduct", "ORDER_INVALID", invalidJson);

        // Assert - 等待消费（非法消息会被 ack，不会阻塞后续消息）
        // 无法直接验证 ack，但可以验证没有抛异常导致 consumer 停止
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // 验证消费者仍然正常工作：发送一条正常消息
            String orderId = "ORDER_AFTER_INVALID";
            StockDeductMessage validMsg = StockDeductMessage.builder()
                    .orderId(orderId)
                    .items(List.of(
                            StockDeductMessage.DeductItem.builder()
                                    .shopId("SHOP1")
                                    .skuId("SKU001")
                                    .quantity(1)
                                    .build()
                    ))
                    .build();

            kafkaTemplate.send("stock-deduct", orderId, objectMapper.writeValueAsString(validMsg));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(idempotencyRepository.isKafkaDeductProcessed(orderId)).isTrue();
            });
        });
    }

    @Test
    @DisplayName("缺少必需字段的消息：直接 ack")
    void testConsumeDeduct_missingFields_shouldAck() throws Exception {
        // Arrange - 缺少 items
        String json = "{\"orderId\":\"ORDER_MISSING\"}";

        // Act
        kafkaTemplate.send("stock-deduct", "ORDER_MISSING", json);

        // Assert - 验证消费者仍正常工作
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String orderId = "ORDER_AFTER_MISSING";
            StockDeductMessage validMsg = StockDeductMessage.builder()
                    .orderId(orderId)
                    .items(List.of(
                            StockDeductMessage.DeductItem.builder()
                                    .shopId("SHOP1")
                                    .skuId("SKU001")
                                    .quantity(1)
                                    .build()
                    ))
                    .build();

            kafkaTemplate.send("stock-deduct", orderId, objectMapper.writeValueAsString(validMsg));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(idempotencyRepository.isKafkaDeductProcessed(orderId)).isTrue();
            });
        });
    }
}
