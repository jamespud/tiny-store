package com.github.spud.tinystore.promotion.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.PromotionApplication;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCheckoutQuoteRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;
import com.github.spud.tinystore.promotion.interfaces.dto.PricingSnapshot;
import com.github.spud.tinystore.promotion.application.service.CheckoutAppService;

/**
 * OrderEventConsumer Integration Test
 *
 * 验证 Kafka 消费者正确处理订单事件并更新优惠券状态
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
	classes = PromotionApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = {
		"spring.cloud.nacos.discovery.enabled=false",
		"spring.cloud.nacos.config.enabled=false"
	}
)
@DisplayName("Order Event Consumer IT")
@SuppressWarnings("resource")
class OrderEventConsumerIT {

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
	private JpaCheckoutQuoteRepository checkoutQuoteRepository;

	@Autowired
	private JpaUserCouponRepository userCouponRepository;

	@Autowired
	private JpaCouponRepository couponRepository;

	@Autowired
	private JpaConsumerEventLogRepository consumerEventLogRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("TRADE_PAID 事件应将优惠券从 LOCKED 转为 USED")
	void testTradePaidEvent_shouldMarkCouponAsUsed() throws Exception {
		// Given: 创建优惠券和用户优惠券
		CouponEntity coupon = createTestCoupon("TEST-COUPON-001");
		couponRepository.save(coupon);

		String lockId = "plk:test-lock-001";
		UserCouponEntity userCoupon = createTestUserCoupon("user-001", coupon.getId(), "LOCKED", lockId);
		userCouponRepository.save(userCoupon);

		// 创建 checkout quote
		String tradeId = "trade-" + UUID.randomUUID();
		CheckoutQuoteEntity quote = createTestQuote(tradeId, lockId);
		checkoutQuoteRepository.save(quote);

		// 构造 Kafka 消息
		String eventId = "evt-" + UUID.randomUUID();
		Map<String, Object> event = new HashMap<>();
		event.put("eventId", eventId);
		event.put("eventType", "TRADE_PAID");
		event.put("aggregateType", "TRADE");
		event.put("aggregateId", tradeId);
		event.put("occurredAt", LocalDateTime.now().toString());
		event.put("traceId", "trace-001");
		event.put("payload", "{\"tradeId\":\"" + tradeId + "\",\"paymentId\":\"pay-001\",\"paidAmountCents\":10000}");

		String message = objectMapper.writeValueAsString(event);

		// When: 发送 Kafka 消息
		kafkaTemplate.send("tinystore.order.general", message).get(10, TimeUnit.SECONDS);

		// Then: 等待消费完成，验证优惠券状态
		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
			Optional<UserCouponEntity> updated = userCouponRepository.findById(userCoupon.getId());
			assertThat(updated).isPresent();
			assertThat(updated.get().getUseStatus()).isEqualTo("USED");
			assertThat(updated.get().getUsedTradeId()).isEqualTo(tradeId);
			assertThat(updated.get().getUsedTime()).isNotNull();
		});

		// 验证幂等日志
		boolean logExists = consumerEventLogRepository.existsByEventIdAndConsumerName(eventId,
			"promotion-order-consumer");
		assertThat(logExists).isTrue();
	}

	@Test
	@DisplayName("TRADE_CLOSED 事件应将优惠券从 LOCKED 转为 UNUSED")
	void testTradeClosedEvent_shouldUnlockCoupon() throws Exception {
		// Given
		CouponEntity coupon = createTestCoupon("TEST-COUPON-002");
		couponRepository.save(coupon);

		String lockId = "plk:test-lock-002";
		UserCouponEntity userCoupon = createTestUserCoupon("user-002", coupon.getId(), "LOCKED", lockId);
		userCouponRepository.save(userCoupon);

		String tradeId = "trade-" + UUID.randomUUID();
		CheckoutQuoteEntity quote = createTestQuote(tradeId, lockId);
		checkoutQuoteRepository.save(quote);

		String eventId = "evt-" + UUID.randomUUID();
		Map<String, Object> event = new HashMap<>();
		event.put("eventId", eventId);
		event.put("eventType", "TRADE_CLOSED");
		event.put("aggregateType", "TRADE");
		event.put("aggregateId", tradeId);
		event.put("occurredAt", LocalDateTime.now().toString());
		event.put("traceId", "trace-002");
		event.put("payload", "{\"tradeId\":\"" + tradeId + "\",\"reason\":\"TIMEOUT\"}");

		String message = objectMapper.writeValueAsString(event);

		// When
		kafkaTemplate.send("tinystore.order.general", message).get(10, TimeUnit.SECONDS);

		// Then
		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
			Optional<UserCouponEntity> updated = userCouponRepository.findById(userCoupon.getId());
			assertThat(updated).isPresent();
			assertThat(updated.get().getUseStatus()).isEqualTo("UNUSED");
			assertThat(updated.get().getLockId()).isNull();
			assertThat(updated.get().getLockExpireTime()).isNull();
		});

		boolean logExists = consumerEventLogRepository.existsByEventIdAndConsumerName(eventId,
			"promotion-order-consumer");
		assertThat(logExists).isTrue();
	}

	@Test
	@DisplayName("重复消费同一 eventId 应保持幂等")
	void testIdempotentConsumption() throws Exception {
		// Given
		CouponEntity coupon = createTestCoupon("TEST-COUPON-003");
		couponRepository.save(coupon);

		String lockId = "plk:test-lock-003";
		UserCouponEntity userCoupon = createTestUserCoupon("user-003", coupon.getId(), "LOCKED", lockId);
		userCouponRepository.save(userCoupon);

		String tradeId = "trade-" + UUID.randomUUID();
		CheckoutQuoteEntity quote = createTestQuote(tradeId, lockId);
		checkoutQuoteRepository.save(quote);

		String eventId = "evt-idempotent-" + UUID.randomUUID();
		Map<String, Object> event = new HashMap<>();
		event.put("eventId", eventId);
		event.put("eventType", "TRADE_PAID");
		event.put("aggregateType", "TRADE");
		event.put("aggregateId", tradeId);
		event.put("occurredAt", LocalDateTime.now().toString());
		event.put("traceId", "trace-003");
		event.put("payload", "{\"tradeId\":\"" + tradeId + "\"}");

		String message = objectMapper.writeValueAsString(event);

		// When: 发送两次相同消息
		kafkaTemplate.send("tinystore.order.general", message).get(10, TimeUnit.SECONDS);
		kafkaTemplate.send("tinystore.order.general", message).get(10, TimeUnit.SECONDS);

		// Then: 优惠券状态只变更一次
		await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
			Optional<UserCouponEntity> updated = userCouponRepository.findById(userCoupon.getId());
			assertThat(updated).isPresent();
			assertThat(updated.get().getUseStatus()).isEqualTo("USED");
		});

		// 幂等日志只有一条
		List<ConsumerEventLogEntity> logs = consumerEventLogRepository.findAll();
		long count = logs.stream().filter(log -> log.getEventId().equals(eventId)).count();
		assertThat(count).isEqualTo(1);
	}

	// Helper methods
	private CouponEntity createTestCoupon(String couponNo) {
		CouponEntity coupon = new CouponEntity();
		coupon.setId(UUID.randomUUID());
		coupon.setCouponNo(couponNo);
		coupon.setCouponType("FIXED");
		coupon.setStatus("ACTIVE");
		coupon.setScopeType("PLATFORM");
		coupon.setDiscountAmount(BigDecimal.valueOf(10));
		coupon.setThresholdAmount(BigDecimal.valueOf(100));
		coupon.setTotalStock(1000);
		coupon.setUsedStock(0);
		coupon.setPriority(1);
		coupon.setStartTime(LocalDateTime.now().minusDays(1));
		coupon.setEndTime(LocalDateTime.now().plusDays(30));
		coupon.setCreatedAt(LocalDateTime.now());
		coupon.setUpdatedAt(LocalDateTime.now());
		return coupon;
	}

	private UserCouponEntity createTestUserCoupon(String userId, UUID couponId, String status, String lockId) {
		UserCouponEntity uc = new UserCouponEntity();
		uc.setId(UUID.randomUUID());
		uc.setUserId(userId);
		uc.setCouponId(couponId);
		uc.setCouponNo("COUPON-NO-" + UUID.randomUUID().toString().substring(0, 8)); // 添加必需的 coupon_no
		uc.setUseStatus(status);
		uc.setLockId(lockId);
		uc.setLockExpireTime(LocalDateTime.now().plusHours(1));
		uc.setReceiveTime(LocalDateTime.now().minusDays(1));
		uc.setCreatedAt(LocalDateTime.now());
		uc.setUpdatedAt(LocalDateTime.now());
		return uc;
	}

	private CheckoutQuoteEntity createTestQuote(String tradeId, String lockId) throws Exception {
		CheckoutQuoteEntity quote = new CheckoutQuoteEntity();
		quote.setId(UUID.randomUUID());
		quote.setUserId("user-test");
		quote.setStatus("COMMITTED");
		quote.setInputHash("test-hash");
		quote.setTradeId(tradeId);
		quote.setExpiresAt(LocalDateTime.now().plusHours(1));
		quote.setCreatedAt(LocalDateTime.now());
		quote.setUpdatedAt(LocalDateTime.now());

		// 构造 snapshot
		PricingSnapshot.AppliedBenefit benefit = new PricingSnapshot.AppliedBenefit();
		benefit.setBenefitType("PLATFORM_COUPON");
		benefit.setBenefitId("TEST-COUPON-001");
		benefit.setLockId(lockId);
		benefit.setAmountCents(-1000L);

		PricingSnapshot snapshot = new PricingSnapshot();
		snapshot.setAppliedBenefits(List.of(benefit));

		CheckoutAppService.CheckoutQuotePayload payload = new CheckoutAppService.CheckoutQuotePayload();
		payload.setSnapshot(snapshot);

		quote.setSnapshot(objectMapper.writeValueAsString(payload));
		return quote;
	}
}
