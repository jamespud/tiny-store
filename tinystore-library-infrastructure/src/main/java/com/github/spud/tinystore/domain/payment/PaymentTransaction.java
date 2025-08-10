package com.github.spud.tinystore.domain.payment;

import com.github.spud.tinystore.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 支付交易实体
 * 对应 payment.payment_transactions 表
 */
@Entity
@Table(name = "payment_transactions", schema = "payment")
public class PaymentTransaction extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;
    
    @Column(name = "external_txn_id", unique = true)
    private String externalTxnId; // 外部交易号（如支付宝交易号）
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;
    
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload; // 原始支付数据
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // 业务方法
    public boolean hasExternalId() {
        return externalTxnId != null && !externalTxnId.trim().isEmpty();
    }
    
    public boolean isSuccess() {
        return PaymentStatus.SUCCESS.equals(status);
    }
    
    public boolean isFailed() {
        return PaymentStatus.FAILED.equals(status);
    }
    
    public boolean isPending() {
        return PaymentStatus.PENDING.equals(status);
    }
    
    public String getStatusDescription() {
        return status != null ? status.getDescription() : "";
    }
    
    // Getters and Setters
    public Payment getPayment() {
        return payment;
    }
    
    public void setPayment(Payment payment) {
        this.payment = payment;
    }
    
    public String getExternalTxnId() {
        return externalTxnId;
    }
    
    public void setExternalTxnId(String externalTxnId) {
        this.externalTxnId = externalTxnId;
    }
    
    public PaymentStatus getStatus() {
        return status;
    }
    
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
    
    public String getRawPayload() {
        return rawPayload;
    }
    
    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
