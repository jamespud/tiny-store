package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付意图 JPA 实体（用于自洽支付闭环）
 */
@Entity
@Table(name = "payment_intent", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 64)
    private String paymentId;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ========== 新增字段：支持命令型事件与支付域协同 ==========

    /**
     * 买家ID（用于支付域创建支付单）
     */
    @Column(name = "buyer_id", length = 64)
    private String buyerId;

    /**
     * 支付渠道（WECHAT/ALIPAY/UNIONPAY等）
     */
    @Column(name = "pay_channel", length = 32)
    private String payChannel;

    /**
     * 支付超时时间（支付域据此关闭支付单）
     */
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    /**
     * 支付域支付单ID（支付域回写，用于关联与对账）
     */
    @Column(name = "payment_order_id", length = 64)
    private String paymentOrderId;

    /**
     * 第三方支付流水号（支付成功后回写，用于幂等与对账）
     */
    @Column(name = "third_trade_no", length = 128)
    private String thirdTradeNo;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
