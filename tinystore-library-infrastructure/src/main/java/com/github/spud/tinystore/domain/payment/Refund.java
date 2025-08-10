package com.github.spud.tinystore.domain.payment;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.ordering.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款实体
 * 对应 payment.refunds 表
 */
@Entity
@Table(name = "refunds", schema = "payment")
public class Refund extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
    
    @Column(name = "refund_sn", nullable = false, unique = true)
    private String refundSn; // 退款单号（业务唯一）
    
    @Column(name = "amount", nullable = false)
    private Long amount; // 退款金额（分）
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status = RefundStatus.REQUESTED;
    
    @Column(name = "reason")
    private String reason; // 退款原因
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 审核通过时间
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt; // 退款完成时间
    
    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundItem> refundItems = new ArrayList<>();
    
    // 业务方法
    public void addRefundItem(RefundItem item) {
        refundItems.add(item);
        item.setRefund(this);
    }
    
    public void removeRefundItem(RefundItem item) {
        refundItems.remove(item);
        item.setRefund(null);
    }
    
    public void approve() {
        this.status = RefundStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }
    
    public void reject() {
        this.status = RefundStatus.REJECTED;
    }
    
    public void markAsProcessing() {
        this.status = RefundStatus.PROCESSING;
    }
    
    public void markAsCompleted() {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = RefundStatus.FAILED;
    }
    
    public boolean isRequested() {
        return RefundStatus.REQUESTED.equals(status);
    }
    
    public boolean isApproved() {
        return RefundStatus.APPROVED.equals(status);
    }
    
    public boolean isRejected() {
        return RefundStatus.REJECTED.equals(status);
    }
    
    public boolean isProcessing() {
        return RefundStatus.PROCESSING.equals(status);
    }
    
    public boolean isCompleted() {
        return RefundStatus.COMPLETED.equals(status);
    }
    
    public boolean isFailed() {
        return RefundStatus.FAILED.equals(status);
    }
    
    public boolean canProcess() {
        return isApproved() && !isCompleted() && !isFailed();
    }
    
    public String getFormattedAmount() {
        return String.format("%.2f", amount / 100.0);
    }
    
    // Getters and Setters
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public Payment getPayment() {
        return payment;
    }
    
    public void setPayment(Payment payment) {
        this.payment = payment;
    }
    
    public String getRefundSn() {
        return refundSn;
    }
    
    public void setRefundSn(String refundSn) {
        this.refundSn = refundSn;
    }
    
    public Long getAmount() {
        return amount;
    }
    
    public void setAmount(Long amount) {
        this.amount = amount;
    }
    
    public RefundStatus getStatus() {
        return status;
    }
    
    public void setStatus(RefundStatus status) {
        this.status = status;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
    
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public List<RefundItem> getRefundItems() {
        return refundItems;
    }
    
    public void setRefundItems(List<RefundItem> refundItems) {
        this.refundItems = refundItems;
    }
}
