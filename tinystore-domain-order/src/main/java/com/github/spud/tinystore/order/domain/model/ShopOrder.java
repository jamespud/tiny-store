package com.github.spud.tinystore.order.domain.model;

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
     * @deprecated 使用 {@link #inventoryOccupyPairs} 替代
     */
    @Deprecated
    @Builder.Default
    private List<String> inventoryPreOccupyIds = new ArrayList<>();

    /**
     * V2 库存占用凭证（shopId + skuId + occupyId）
     */
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
}
