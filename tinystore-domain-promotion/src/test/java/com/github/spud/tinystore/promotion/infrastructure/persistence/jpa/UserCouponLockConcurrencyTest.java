package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.spud.tinystore.promotion.PromotionApplication;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.UserCouponEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaCouponRepository;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaUserCouponRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PromotionApplication.class)
class UserCouponLockConcurrencyTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
		.withDatabaseName("tinystore")
		.withUsername("postgres")
		.withPassword("postgres");

	@Container
	static final GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4.0"))
		.withExposedPorts(6379);

	@DynamicPropertySource
	static void registerProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);

		registry.add("spring.cloud.discovery.enabled", () -> "false");
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration/promotion");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379).toString());
	}

	@Autowired
	private JpaCouponRepository couponRepository;

	@Autowired
	private JpaUserCouponRepository userCouponRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void lockUnused_shouldAllowOnlyOneWinner() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		UUID couponId = UUID.randomUUID();
		CouponEntity coupon = new CouponEntity();
		coupon.setId(couponId);
		coupon.setCouponNo("C" + couponId.toString().substring(0, 8));
		coupon.setCouponType("PLATFORM");
		coupon.setShopId(null);
		coupon.setTotalStock(100);
		coupon.setUsedStock(0);
		coupon.setStartTime(now.minusDays(1));
		coupon.setEndTime(now.plusDays(1));
		coupon.setMutexGroup("G1");
		coupon.setPriority(0);
		coupon.setStatus("ACTIVE");
		coupon.setCreatedAt(now);
		coupon.setUpdatedAt(now);
		couponRepository.save(coupon);

		UUID userCouponId = UUID.randomUUID();
		UserCouponEntity userCoupon = new UserCouponEntity();
		userCoupon.setId(userCouponId);
		userCoupon.setUserId("U1");
		userCoupon.setCouponId(couponId);
		userCoupon.setCouponNo(coupon.getCouponNo());
		userCoupon.setReceiveTime(now);
		userCoupon.setUseStatus("UNUSED");
		userCoupon.setCreatedAt(now);
		userCoupon.setUpdatedAt(now);
		userCouponRepository.save(userCoupon);

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				String lockId = "L" + i + "-" + UUID.randomUUID();
				tasks.add(() -> tx.execute(status -> userCouponRepository.lockUnused(
					userCouponId,
					lockId,
					now.plusMinutes(30),
					LocalDateTime.now()
				)));
			}
			List<Future<Integer>> results = pool.invokeAll(tasks);
			int success = 0;
			for (Future<Integer> f : results) {
				success += f.get();
			}
			assertThat(success).isEqualTo(1);

			UserCouponEntity locked = userCouponRepository.findById(userCouponId).orElseThrow();
			assertThat(locked.getUseStatus()).isEqualTo("LOCKED");
			assertThat(locked.getLockId()).isNotBlank();
		} finally {
			pool.shutdownNow();
		}
	}
}

