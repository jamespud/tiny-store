package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.PayStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Trade 聚合根 - 交易主单
 * 
 * 职责：
 * - 管理交易整体支付状态
 * - 协调子单生命周期
 * - 计算交易总金额/优惠/应付
 */
@Getter
@Builder
public class Trade {
    
    private Long id;
    @Builder.Default
    private Long version = 0L;
    private String tradeId;
    private String buyerId;
    private String buyerNick;
    
    private PayStatus payStatus;
    
    private Long totalAmountCents;
    private Long discountAmountCents;
    private Long payableAmountCents;
    
    @Builder.Default
    private List<ShopOrder> shopOrders = new ArrayList<>();
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    
    private String promotionQuoteId;
    private String promotionInputHash;
    private String inventoryReservationId;

    @Builder.Default
    private String promotionCommitStatus = "PENDING"; // PENDING / COMMITTED / FAILED
    
    @Builder.Default
    private List<String> couponCodes = new ArrayList<>();
    
    /**
     * 检查交易是否已关闭（优先判定 closedAt）
     */
    public boolean isClosed() {
        return this.closedAt != null;
    }
    
    /**
     * 校验 promotion quote 关联已绑定（关键路径使用前校验）
     */
    public void requirePromotionQuoteBound() {
        if (this.promotionQuoteId == null || this.promotionQuoteId.isEmpty()) {
            throw new IllegalStateException("Promotion quote ID not bound for trade: " + tradeId);
        }
        if (this.promotionInputHash == null || this.promotionInputHash.isEmpty()) {
            throw new IllegalStateException("Promotion input hash not bound for trade: " + tradeId);
        }
    }
    
    /**
     * 校验 inventory reservation 关联已绑定（关键路径使用前校验）
     */
    public void requireInventoryReservationBound() {
        if (this.inventoryReservationId == null || this.inventoryReservationId.isEmpty()) {
            throw new IllegalStateException("Inventory reservation ID not bound for trade: " + tradeId);
        }
    }
    
    /**
     * 支付成功
     */
    public void markAsPaid() {
        if (this.payStatus != PayStatus.UNPAID) {
            throw new IllegalStateException("Trade must be UNPAID to mark as PAID: " + tradeId);
        }
        this.payStatus = PayStatus.PAID;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 部分退款
     */
    public void markAsPartRefunded() {
        if (this.payStatus != PayStatus.PAID && this.payStatus != PayStatus.PART_REFUNDED) {
            throw new IllegalStateException("Trade must be PAID or PART_REFUNDED to add refund: " + tradeId);
        }
        this.payStatus = PayStatus.PART_REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 全额退款
     */
    public void markAsRefunded() {
        this.payStatus = PayStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 检查是否可取消（仅未支付可取消）
     */
    public boolean canCancel() {
        return this.payStatus == PayStatus.UNPAID;
    }

    /**
     * 标记 promotion commit 成功（由回执消费端调用）
     */
    public void markPromotionCommitted() {
        this.promotionCommitStatus = "COMMITTED";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记 promotion commit 失败（由回执消费端调用，随后触发自动取消）
     */
    public void markPromotionCommitFailed() {
        this.promotionCommitStatus = "FAILED";
        this.updatedAt = LocalDateTime.now();
    }
    
    public void closeTrade() {
        if (isClosed() || !canCancel()) {
            throw new IllegalStateException("Trade is already closed: " + tradeId);
        }
        this.closedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
