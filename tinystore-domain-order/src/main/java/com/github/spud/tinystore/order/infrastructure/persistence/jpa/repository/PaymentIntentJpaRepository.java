package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PaymentIntent JPA Repository
 */
@Repository
public interface PaymentIntentJpaRepository extends JpaRepository<PaymentIntentEntity, Long> {

    Optional<PaymentIntentEntity> findByPaymentId(String paymentId);

    Optional<PaymentIntentEntity> findByTradeId(String tradeId);
}
