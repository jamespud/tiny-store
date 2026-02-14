package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_deduct_record", schema = "tinystore_inventory")
@Data
@Accessors(chain = true)
public class InventoryDeductRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 128)
    private String orderId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "shop_id", nullable = false, length = 100)
    private String shopId;

    @Column(name = "sku_id", nullable = false, length = 128)
    private String skuId;

    @Column(name = "occupy_id", nullable = false, length = 512)
    private String occupyId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "release_reason", length = 128)
    private String releaseReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "DEDUCTED";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
