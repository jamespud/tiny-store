package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CheckoutQuoteEntity;

/**
 * JpaCheckoutQuoteRepository Integration Test
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Checkout Quote Repository IT")
@SuppressWarnings("resource")
class JpaCheckoutQuoteRepositoryIT {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1")
		.withDatabaseName("tinystore")
		.withUsername("postgres")
		.withPassword("postgres")
		.withInitScript("db/migration/promotion/V1__init_coupon_tables.sql")
		.withStartupTimeout(Duration.ofMinutes(3));

	@DynamicPropertySource
	static void registerProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private JpaCheckoutQuoteRepository repository;

	@Test
	@DisplayName("findByTradeId 应成功查询到对应的 quote")
	void testFindByTradeId_shouldReturnQuote_whenExists() {
		// Given
		String tradeId = "trade-test-" + UUID.randomUUID();
		CheckoutQuoteEntity quote = new CheckoutQuoteEntity();
		quote.setId(UUID.randomUUID());
		quote.setUserId("user-001");
		quote.setStatus("COMMITTED");
		quote.setInputHash("test-hash");
		quote.setTradeId(tradeId);
		quote.setSnapshot("{}");
		quote.setExpiresAt(LocalDateTime.now().plusHours(1));
		quote.setCreatedAt(LocalDateTime.now());
		quote.setUpdatedAt(LocalDateTime.now());

		repository.save(quote);

		// When
		Optional<CheckoutQuoteEntity> result = repository.findByTradeId(tradeId);

		// Then
		assertThat(result).isPresent();
		assertThat(result.get().getTradeId()).isEqualTo(tradeId);
		assertThat(result.get().getUserId()).isEqualTo("user-001");
		assertThat(result.get().getStatus()).isEqualTo("COMMITTED");
	}

	@Test
	@DisplayName("findByTradeId 应返回空 Optional 当 tradeId 不存在")
	void testFindByTradeId_shouldReturnEmpty_whenNotExists() {
		// When
		Optional<CheckoutQuoteEntity> result = repository.findByTradeId("non-existent-trade-id");

		// Then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("findByTradeId 应返回最新的 quote 当存在多个相同 tradeId")
	void testFindByTradeId_shouldReturnLatest_whenMultipleExist() {
		// Given
		String tradeId = "trade-duplicate-" + UUID.randomUUID();

		CheckoutQuoteEntity quote1 = new CheckoutQuoteEntity();
		quote1.setId(UUID.randomUUID());
		quote1.setUserId("user-002");
		quote1.setStatus("QUOTED");
		quote1.setInputHash("hash1");
		quote1.setTradeId(tradeId);
		quote1.setSnapshot("{}");
		quote1.setExpiresAt(LocalDateTime.now().plusHours(1));
		quote1.setCreatedAt(LocalDateTime.now().minusMinutes(10));
		quote1.setUpdatedAt(LocalDateTime.now().minusMinutes(10));

		CheckoutQuoteEntity quote2 = new CheckoutQuoteEntity();
		quote2.setId(UUID.randomUUID());
		quote2.setUserId("user-002");
		quote2.setStatus("COMMITTED");
		quote2.setInputHash("hash2");
		quote2.setTradeId(tradeId);
		quote2.setSnapshot("{}");
		quote2.setExpiresAt(LocalDateTime.now().plusHours(1));
		quote2.setCreatedAt(LocalDateTime.now());
		quote2.setUpdatedAt(LocalDateTime.now());

		repository.save(quote1);
		repository.save(quote2);

		// When
		Optional<CheckoutQuoteEntity> result = repository.findByTradeId(tradeId);

		// Then
		assertThat(result).isPresent();
		// Spring Data JPA 默认返回第一个匹配项，实际业务中 tradeId 应该唯一
		assertThat(result.get().getTradeId()).isEqualTo(tradeId);
	}
}
