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
}
