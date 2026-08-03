package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 交易主单 JPA 实体
 */
@Entity
@Table(name = "trade", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 64)
    private String tradeId;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "buyer_nick", length = 255)
    private String buyerNick;

    @Column(name = "pay_status", nullable = false, length = 32)
    private String payStatus;

    @Column(name = "total_amount_cents", nullable = false)
    private Long totalAmountCents;

    @Column(name = "discount_amount_cents")
    private Long discountAmountCents;

    @Column(name = "payable_amount_cents", nullable = false)
    private Long payableAmountCents;

    @Column(name = "pay_type", length = 32)
    private String payType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "promotion_quote_id", length = 128)
    private String promotionQuoteId;

    @Column(name = "promotion_input_hash", length = 255)
    private String promotionInputHash;

    @Column(name = "inventory_reservation_id", length = 128)
    private String inventoryReservationId;

    @Builder.Default
    @Column(name = "promotion_commit_status", nullable = false)
    private String promotionCommitStatus = "PENDING";

    @Column(name = "coupon_code", length = 128)
    private String couponCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coupon_codes", columnDefinition = "jsonb")
    private String couponCodes;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
