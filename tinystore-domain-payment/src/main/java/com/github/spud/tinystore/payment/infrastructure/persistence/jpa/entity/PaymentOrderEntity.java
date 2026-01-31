package com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 支付单 JPA 实体
 * 支付域聚合根
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Entity
@Table(name = "payment_order", schema = "tinystore_payment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 支付单ID（支付域内部唯一标识）
     */
    @Column(name = "payment_order_id", nullable = false, unique = true, length = 64)
    private String paymentOrderId;

    /**
     * 支付意图ID（订单域传入，幂等主键）
     */
    @Column(name = "payment_intent_id", nullable = false, unique = true, length = 64)
    private String paymentIntentId;

    /**
     * 交易ID（关联订单域）
     */
    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    /**
     * 买家ID
     */
    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    /**
     * 支付金额（分）
     */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    /**
     * 支付渠道：WECHAT/ALIPAY/UNIONPAY/DEFAULT
     */
    @Column(name = "pay_channel", nullable = false, length = 32)
    private String payChannel;

    /**
     * 支付状态：UNPAID/PAID/CLOSED/EXPIRED/FAILED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * 第三方支付流水号
     */
    @Column(name = "third_trade_no", length = 128)
    private String thirdTradeNo;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 支付成功时间
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * 关闭时间
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * 支付超时时间
     */
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
