package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.AfterSaleCaseEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AfterSaleCase JPA Repository
 */
@Repository
public interface AfterSaleCaseJpaRepository extends JpaRepository<AfterSaleCaseEntity, Long> {

    Optional<AfterSaleCaseEntity> findByCaseId(String caseId);

    List<AfterSaleCaseEntity> findByTradeId(String tradeId);

    List<AfterSaleCaseEntity> findByOrderId(String orderId);

    Optional<AfterSaleCaseEntity> findByRefundId(String refundId);
}
