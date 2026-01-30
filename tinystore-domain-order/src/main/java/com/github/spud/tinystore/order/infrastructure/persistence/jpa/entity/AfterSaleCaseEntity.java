package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后单 JPA 实体
 */
@Entity
@Table(name = "after_sale_case", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AfterSaleCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false, unique = true, length = 64)
    private String caseId;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "case_type", nullable = false, length = 32)
    private String caseType;

    @Column(name = "case_status", nullable = false, length = 32)
    private String caseStatus;

    @Column(name = "refund_amount_cents")
    private Long refundAmountCents;

    @Column(name = "refund_id", length = 64)
    private String refundId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
