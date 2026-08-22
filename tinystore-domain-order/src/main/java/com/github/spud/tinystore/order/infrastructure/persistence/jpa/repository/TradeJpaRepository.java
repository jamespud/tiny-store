package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Trade JPA Repository
 */
@Repository
public interface TradeJpaRepository extends JpaRepository<TradeEntity, Long> {

    Optional<TradeEntity> findByTradeId(String tradeId);

    List<TradeEntity> findByBuyerId(String buyerId);

    @Query("SELECT t FROM TradeEntity t WHERE t.payStatus = :payStatus AND t.createdAt < :before ORDER BY t.createdAt ASC")
    List<TradeEntity> findPendingPaymentsByTimeout(@Param("payStatus") String payStatus, @Param("before") LocalDateTime before);

    @Query("SELECT t.tradeId FROM TradeEntity t WHERE t.promotionCommitStatus = 'PENDING' "
            + "AND t.payStatus = 'UNPAID' AND t.createdAt < :threshold")
    List<String> findStalePendingCommitTradeIds(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT t.tradeId FROM TradeEntity t WHERE t.payStatus = 'PAID' "
            + "AND t.closedAt IS NULL AND t.updatedAt < :before ORDER BY t.updatedAt ASC")
    List<String> findPaidTradeIdsSince(@Param("before") LocalDateTime before);
}
