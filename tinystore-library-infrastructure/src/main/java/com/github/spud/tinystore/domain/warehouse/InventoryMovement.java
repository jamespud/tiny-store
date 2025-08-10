package com.github.spud.tinystore.domain.warehouse;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.catalog.ProductSku;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 库存变动实体
 * 对应 warehouse.inventory_movements 表
 */
@Entity
@Table(name = "inventory_movements", schema = "warehouse")
public class InventoryMovement extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private ProductSku sku;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private InventoryMovementType movementType;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "reference_type")
    private String referenceType; // 关联业务类型（如"ORDER"、"REFUND"）
    
    @Column(name = "reference_id")
    private UUID referenceId; // 关联业务ID（如订单ID）
    
    @Column(name = "note")
    private String note; // 备注
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 业务方法
    public boolean isIncrease() {
        return movementType != null && movementType.isIncrease();
    }
    
    public boolean isDecrease() {
        return movementType != null && movementType.isDecrease();
    }
    
    public boolean isAdjustment() {
        return movementType != null && movementType.isAdjustment();
    }
    
    public boolean hasReference() {
        return referenceType != null && referenceId != null;
    }
    
    public String getMovementDescription() {
        if (movementType == null) {
            return "";
        }
        String direction = isIncrease() ? "增加" : isDecrease() ? "减少" : "调整";
        return String.format("%s %d 件", direction, Math.abs(quantity));
    }
    
    public String getFormattedQuantity() {
        if (quantity == null) {
            return "0";
        }
        return isIncrease() ? "+" + quantity : String.valueOf(quantity);
    }
    
    // Getters and Setters
    public Warehouse getWarehouse() {
        return warehouse;
    }
    
    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }
    
    public ProductSku getSku() {
        return sku;
    }
    
    public void setSku(ProductSku sku) {
        this.sku = sku;
    }
    
    public InventoryMovementType getMovementType() {
        return movementType;
    }
    
    public void setMovementType(InventoryMovementType movementType) {
        this.movementType = movementType;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public String getReferenceType() {
        return referenceType;
    }
    
    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }
    
    public UUID getReferenceId() {
        return referenceId;
    }
    
    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
