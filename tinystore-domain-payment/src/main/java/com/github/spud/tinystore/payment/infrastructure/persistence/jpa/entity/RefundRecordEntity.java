package com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 退款记录 JPA 实体
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Entity
@Table(name = "refund_record", schema = "tinystore_payment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 退款单号（订单域传入，幂等主键）
     */
    @Column(name = "refund_id", nullable = false, unique = true, length = 64)
    private String refundId;

    /**
     * 支付意图ID（关联订单域）
     */
    @Column(name = "payment_intent_id", nullable = false, length = 64)
    private String paymentIntentId;

    /**
     * 支付单ID（关联支付域）
     */
    @Column(name = "payment_order_id", nullable = false, length = 64)
    private String paymentOrderId;

    /**
     * 交易ID（关联订单域）
     */
    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    /**
     * 退款金额（分）
     */
    @Column(name = "refund_amount_cents", nullable = false)
    private Long refundAmountCents;

    /**
     * 退款状态：REQUESTED/PROCESSING/SUCCESS/FAIL
     */
    @Column(name = "refund_status", nullable = false, length = 32)
    private String refundStatus;

    /**
     * 第三方退款流水号
     */
    @Column(name = "third_refund_no", length = 128)
    private String thirdRefundNo;

    /**
     * 请求退款时间
     */
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /**
     * 退款成功时间
     */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /**
     * 通知订单域的时间戳
     */
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    /**
     * 通知状态：PENDING/SUCCESS/FAILED
     */
    @Column(name = "notification_status", length = 32)
    private String notificationStatus;

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
