package com.github.spud.tinystore.domain.payment;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.ordering.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付实体
 * 对应 payment.payments 表
 */
@Entity
@Table(name = "payments", schema = "payment")
public class Payment extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Column(name = "payment_sn", nullable = false, unique = true)
    private String paymentSn; // 支付单号（业务唯一）
    
    @Column(name = "amount", nullable = false)
    private Long amount; // 支付金额（分）
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "CNY";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.INIT;
    
    @Column(name = "channel", nullable = false)
    private String channel; // 支付渠道（如"ALIPAY"、"WECHAT"）
    
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload; // 支付请求参数
    
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload; // 支付响应结果
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt; // 支付成功时间
    
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> transactions = new ArrayList<>();
    
    // 业务方法
    public void addTransaction(PaymentTransaction transaction) {
        transactions.add(transaction);
        transaction.setPayment(this);
    }
    
    public void removeTransaction(PaymentTransaction transaction) {
        transactions.remove(transaction);
        transaction.setPayment(null);
    }
    
    public void markAsSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.paidAt = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }
    
    public void markAsClosed() {
        this.status = PaymentStatus.CLOSED;
    }
    
    public boolean isSuccess() {
        return PaymentStatus.SUCCESS.equals(status);
    }
    
    public boolean isPending() {
        return PaymentStatus.PENDING.equals(status);
    }
    
    public boolean canRefund() {
        return isSuccess() && !PaymentStatus.REFUNDED.equals(status);
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
    
    public String getPaymentSn() {
        return paymentSn;
    }
    
    public void setPaymentSn(String paymentSn) {
        this.paymentSn = paymentSn;
    }
    
    public Long getAmount() {
        return amount;
    }
    
    public void setAmount(Long amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public PaymentStatus getStatus() {
        return status;
    }
    
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public String getRequestPayload() {
        return requestPayload;
    }
    
    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }
    
    public String getResponsePayload() {
        return responsePayload;
    }
    
    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getPaidAt() {
        return paidAt;
    }
    
    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
    
    public List<PaymentTransaction> getTransactions() {
        return transactions;
    }
    
    public void setTransactions(List<PaymentTransaction> transactions) {
        this.transactions = transactions;
    }
}
