package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店铺子单 JPA 实体
 */
@Entity
@Table(name = "shop_order", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "order_status", nullable = false, length = 32)
    private String orderStatus;

    @Column(name = "inventory_status", nullable = false, length = 32)
    private String inventoryStatus;

    @Column(name = "promotion_status", nullable = false, length = 32)
    private String promotionStatus;

    @Column(name = "inventory_pre_occupy_ids_json", columnDefinition = "TEXT")
    private String inventoryPreOccupyIdsJson;

    /**
     * Canonical inventory reservation refs JSON (version 2 orders).
     * Format: [{shopId, skuId, reservationId}]
     */
    @Column(name = "inventory_reservation_refs_json", columnDefinition = "TEXT")
    private String inventoryReservationRefsJson;

    /**
     * Inventory projection version: 1=legacy V2, 2=canonical reservation.
     * Default 1 for backward compatibility.
     */
    @Column(name = "inventory_projection_version", nullable = false)
    @Builder.Default
    private int inventoryProjectionVersion = 1;

    @Column(name = "logistics_status", length = 32)
    private String logisticsStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

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
