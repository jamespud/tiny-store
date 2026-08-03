package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.test.it.AbstractOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trade JPA Repository 集成测试（使用 PostgreSQL Testcontainers）
 * 
 * 运行方式：
 * - 标准 `./mvnw test` 会跳过此测试（*IT.java 不在默认运行范围）
 * - 显式运行集成测试需要 Docker 且执行：`./mvnw verify`
 */
@DataJpaTest
class TradeJpaRepositoryIT extends AbstractOrderIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TradeJpaRepository tradeJpaRepository;

    private String testTradeId;
    private String testBuyerId;

    @BeforeEach
    void setUp() {
        testTradeId = UUID.randomUUID().toString();
        testBuyerId = "buyer_" + UUID.randomUUID().toString().substring(0, 8);

        TradeEntity trade = TradeEntity.builder()
            .tradeId(testTradeId)
            .buyerId(testBuyerId)
            .buyerNick("test_buyer")
            .payStatus("UNPAID")
            .totalAmountCents(100000L)
            .discountAmountCents(10000L)
            .payableAmountCents(90000L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        entityManager.persist(trade);
        entityManager.flush();
    }

    @Test
    void testFindByTradeId() {
        // When
        Optional<TradeEntity> result = tradeJpaRepository.findByTradeId(testTradeId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testTradeId, result.get().getTradeId());
        assertEquals(testBuyerId, result.get().getBuyerId());
        assertEquals("UNPAID", result.get().getPayStatus());
    }

    @Test
    void testFindByBuyerId() {
        // When
        List<TradeEntity> results = tradeJpaRepository.findByBuyerId(testBuyerId);

        // Then
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(t -> t.getTradeId().equals(testTradeId)));
    }

    @Test
    void testFindByTradeIdNotFound() {
        // When
        Optional<TradeEntity> result = tradeJpaRepository.findByTradeId("non_existent_id");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testSaveAndRetrievePayStatusUpdate() {
        // Given
        TradeEntity trade = entityManager.find(TradeEntity.class,
            tradeJpaRepository.findByTradeId(testTradeId).get().getId());

        // When
        trade.setPayStatus("PAID");
        entityManager.flush();

        // Then
        TradeEntity updated = tradeJpaRepository.findByTradeId(testTradeId).get();
        assertEquals("PAID", updated.getPayStatus());
    }

    @Test
    void testDomainTradeSecondSaveSucceeds() {
        // Given: @DataJpaTest 不含 domain repo——手动构造（依赖 jpaRepository + ObjectMapper）
        com.github.spud.tinystore.order.infrastructure.persistence.repository.TradeRepositoryImpl repo =
            new com.github.spud.tinystore.order.infrastructure.persistence.repository.TradeRepositoryImpl(
                tradeJpaRepository, new com.fasterxml.jackson.databind.ObjectMapper());
        com.github.spud.tinystore.order.domain.model.Trade domainTrade =
            repo.findByTradeId(testTradeId).orElseThrow();

        // When: 修改并保存两次（模拟 回执→支付回调 连续修改路径）
        domainTrade.markPromotionCommitted();
        repo.save(domainTrade);
        domainTrade.markPromotionCommitFailed();
        repo.save(domainTrade);

        // Then: 第二次 save 无 ObjectOptimisticLockingFailureException
        com.github.spud.tinystore.order.domain.model.Trade reloaded =
            repo.findByTradeId(testTradeId).orElseThrow();
        assertEquals("FAILED", reloaded.getPromotionCommitStatus());
    }
}
