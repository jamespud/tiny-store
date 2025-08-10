package com.github.spud.tinystore.domain.warehouse;

import com.github.spud.tinystore.domain.catalog.ProductSku;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * 库存实体类，对应warehouse.inventory表
 */
@Entity
@Table(name = "inventory", schema = "warehouse")
public class Inventory {

    @EmbeddedId
    private InventoryId id;

    @NotNull
    @PositiveOrZero
    @Column(name = "quantity_available", nullable = false)
    private Integer quantityAvailable = 0;

    @NotNull
    @PositiveOrZero
    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    // Constructors
    public Inventory() {}

    public Inventory(Warehouse warehouse, ProductSku sku) {
        this.id = new InventoryId(warehouse.getId(), sku.getId());
    }

    public Inventory(Warehouse warehouse, ProductSku sku, Integer quantityAvailable) {
        this(warehouse, sku);
        this.quantityAvailable = quantityAvailable;
    }

    // Getters and Setters
    public InventoryId getId() {
        return id;
    }

    public void setId(InventoryId id) {
        this.id = id;
    }

    public Integer getQuantityAvailable() {
        return quantityAvailable;
    }

    public void setQuantityAvailable(Integer quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }

    public Integer getQuantityReserved() {
        return quantityReserved;
    }

    public void setQuantityReserved(Integer quantityReserved) {
        this.quantityReserved = quantityReserved;
    }

    // Helper methods
    public Integer getTotalQuantity() {
        return quantityAvailable + quantityReserved;
    }

    public boolean hasAvailableStock(Integer quantity) {
        return quantityAvailable >= quantity;
    }

    public void reserveStock(Integer quantity) {
        if (quantityAvailable < quantity) {
            throw new IllegalStateException("Insufficient available stock");
        }
        quantityAvailable -= quantity;
        quantityReserved += quantity;
    }

    public void releaseReservedStock(Integer quantity) {
        if (quantityReserved < quantity) {
            throw new IllegalStateException("Insufficient reserved stock");
        }
        quantityReserved -= quantity;
        quantityAvailable += quantity;
    }

    public void addStock(Integer quantity) {
        quantityAvailable += quantity;
    }

    public void reduceStock(Integer quantity) {
        if (quantityAvailable < quantity) {
            throw new IllegalStateException("Insufficient available stock");
        }
        quantityAvailable -= quantity;
    }

    /**
     * 库存复合主键
     */
    @Embeddable
    public static class InventoryId {
        @Column(name = "warehouse_id", nullable = false)
        private UUID warehouseId;

        @Column(name = "sku_id", nullable = false)
        private UUID skuId;

        public InventoryId() {}

        public InventoryId(UUID warehouseId, UUID skuId) {
            this.warehouseId = warehouseId;
            this.skuId = skuId;
        }

        // Getters and Setters
        public UUID getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(UUID warehouseId) {
            this.warehouseId = warehouseId;
        }

        public UUID getSkuId() {
            return skuId;
        }

        public void setSkuId(UUID skuId) {
            this.skuId = skuId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InventoryId that = (InventoryId) o;
            return warehouseId.equals(that.warehouseId) && skuId.equals(that.skuId);
        }

        @Override
        public int hashCode() {
            return warehouseId.hashCode() * 31 + skuId.hashCode();
        }
    }
}
