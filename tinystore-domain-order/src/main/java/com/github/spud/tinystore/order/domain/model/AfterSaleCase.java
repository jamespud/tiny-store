package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.enums.AfterSaleType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * AfterSaleCase 聚合根 - 售后案件
 * 
 * 职责：
 * - 管理售后申请、审批、退款流程
 */
@Getter
@Builder
public class AfterSaleCase {
    
    private Long id;
    private String caseId;
    private String tradeId;
    private String orderId;
    private String buyerId;
    private String sellerId;

    private AfterSaleType caseType;
    private AfterSaleStatus caseStatus;
    
    private String reason;
    private Long refundAmountCents;
    
    private String refundId;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime finishedAt;
    
    /**
     * 审批通过
     */
    public void approve() {
        if (this.caseStatus != AfterSaleStatus.APPLIED) {
            throw new IllegalStateException("Case must be APPLIED to approve: " + caseId);
        }
        this.caseStatus = AfterSaleStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 拒绝
     */
    public void reject() {
        if (this.caseStatus != AfterSaleStatus.APPLIED) {
            throw new IllegalStateException("Case must be APPLIED to reject: " + caseId);
        }
        this.caseStatus = AfterSaleStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 进入退款中
     */
    public void startRefunding(String refundId, Long refundAmountCents) {
        if (this.caseStatus != AfterSaleStatus.APPROVED) {
            throw new IllegalStateException("Case must be APPROVED to start refunding: " + caseId);
        }
        this.caseStatus = AfterSaleStatus.REFUNDING;
        this.refundId = refundId;
        this.refundAmountCents = refundAmountCents;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 退款成功
     */
    public void markAsRefunded(String refundId) {
        if (this.caseStatus != AfterSaleStatus.REFUNDING) {
            throw new IllegalStateException("Case must be REFUNDING to mark refunded: " + caseId);
        }
        this.caseStatus = AfterSaleStatus.REFUNDED;
        this.refundId = refundId;
        this.refundedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
