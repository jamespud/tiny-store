package com.github.spud.tinystore.domain.warehouse;

/**
 * 库存变动类型枚举
 * 对应 inventory_movement_type 类型
 */
public enum InventoryMovementType {
    INBOUND("入库"),
    OUTBOUND("出库"),
    RESERVE("预留"),
    RELEASE("释放"),
    ADJUST("调整");
    
    private final String description;
    
    InventoryMovementType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isIncrease() {
        return this == INBOUND || this == RELEASE;
    }
    
    public boolean isDecrease() {
        return this == OUTBOUND || this == RESERVE;
    }
    
    public boolean isAdjustment() {
        return this == ADJUST;
    }
}
