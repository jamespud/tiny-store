package com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.PaymentOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 支付单 JPA Repository
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Repository
public interface PaymentOrderJpaRepository extends JpaRepository<PaymentOrderEntity, Long> {

    /**
     * 根据支付意图ID查询（幂等主键，订单域传入）
     */
    Optional<PaymentOrderEntity> findByPaymentIntentId(String paymentIntentId);

    /**
     * 根据支付单ID查询（支付域内部唯一标识）
     */
    Optional<PaymentOrderEntity> findByPaymentOrderId(String paymentOrderId);

    /**
     * 根据交易ID查询
     */
    Optional<PaymentOrderEntity> findByTradeId(String tradeId);

    /**
     * 查找需要重试通知的记录（已支付但通知未成功）
     */
    Page<PaymentOrderEntity> findByStatusAndNotificationStatusNot(String status, String notificationStatus, Pageable pageable);

}
