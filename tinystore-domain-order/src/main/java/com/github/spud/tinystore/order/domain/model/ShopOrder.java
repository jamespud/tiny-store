package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.InventoryProjectionVersion;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ShopOrder 聚合根 - 店铺子单
 * 
 * 职责：
 * - 管理单个店铺的订单履约状态
 * - 协调订单行与包裹
 */
@Getter
@Builder
public class ShopOrder {
    
    private Long id;
    private String orderId;
    private String tradeId;
    
    private String shopId;
    private String sellerId;
    
    private OrderStatus orderStatus;
    
    private String inventoryStatus;
    private String promotionStatus;

    /**
     * Inventory projection version.
        * VERSION_1 = legacy occupy-pair projection for backward compatibility.
     * VERSION_2 = canonical reservation (InventoryReservationRef, PRE_DEDUCTED/CONFIRMED vocabulary).
     */
    @Builder.Default
    private InventoryProjectionVersion inventoryProjectionVersion = InventoryProjectionVersion.VERSION_1;

    /**
     * Canonical inventory reservation refs (version 2 orders only).
     * reservationId == occupyId from the V2 path.
     */
    @Builder.Default
    private List<InventoryReservationRef> inventoryReservationRefs = new ArrayList<>();

    /**
     * Legacy V2 inventory occupation pairs (version 1 orders).
     *
     * @deprecated Use inventoryReservationRefs for version 2 orders.
     */
    @Deprecated
    @Builder.Default
    private List<InventoryOccupyPair> inventoryOccupyPairs = new ArrayList<>();
    
    private Long totalAmountCents;
    
    @Builder.Default
    private List<OrderLine> orderLines = new ArrayList<>();
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime acceptedAt;
    
    /**
     * 商家接单
     */
    public void accept() {
        if (this.orderStatus != OrderStatus.PENDING_SHIP) {
            throw new IllegalStateException("Order must be PENDING_SHIP to accept: " + orderId);
        }
        this.acceptedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 标记为待收货
     */
    public void markAsPendingReceive() {
        if (this.orderStatus != OrderStatus.PENDING_SHIP) {
            throw new IllegalStateException("Order must be PENDING_SHIP to mark PENDING_RECEIVE: " + orderId);
        }
        this.orderStatus = OrderStatus.PENDING_RECEIVE;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 确认收货/完成
     */
    public void markAsSuccess() {
        if (this.orderStatus != OrderStatus.PENDING_RECEIVE) {
            throw new IllegalStateException("Order must be PENDING_RECEIVE to mark SUCCESS: " + orderId);
        }
        this.orderStatus = OrderStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 关闭订单
     */
    public void close() {
        this.orderStatus = OrderStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记库存已释放
     */
    public void markInventoryReleased() {
        this.inventoryStatus = InventoryStatus.RELEASED.getCode();
        this.updatedAt = LocalDateTime.now();
    }
}
