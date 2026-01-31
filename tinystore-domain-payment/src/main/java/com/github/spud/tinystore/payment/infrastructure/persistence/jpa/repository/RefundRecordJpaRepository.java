package com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.RefundRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 退款记录 JPA Repository
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Repository
public interface RefundRecordJpaRepository extends JpaRepository<RefundRecordEntity, Long> {

    /**
     * 根据退款ID查询（幂等主键，订单域传入）
     */
    Optional<RefundRecordEntity> findByRefundId(String refundId);

    /**
     * 根据支付单ID查询
     */
    Optional<RefundRecordEntity> findByPaymentOrderId(String paymentOrderId);

    /**
     * 查找需要重试通知的记录（退款成功但通知未成功）
     */
    Page<RefundRecordEntity> findByRefundStatusAndNotificationStatusNot(String refundStatus, String notificationStatus, Pageable pageable);

}
