package com.github.spud.tinystore.domain.ordering;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.payment.PaymentStatus;
import com.github.spud.tinystore.domain.payment.ShippingStatus;
import com.github.spud.tinystore.domain.security.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单实体
 * 对应 ordering.orders 表
 */
@Entity
@Table(name = "orders", schema = "ordering")
public class Order extends BaseEntity {
    
    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING;
    
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount; // 总金额（分）
    
    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L; // 优惠金额（分）
    
    @Column(name = "pay_amount", nullable = false)
    private Long payAmount; // 实付金额（分）
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "CNY";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.INIT;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_status", nullable = false)
    private ShippingStatus shippingStatus = ShippingStatus.PENDING;
    
    @Column(name = "address_snapshot", columnDefinition = "jsonb")
    private String addressSnapshot; // 收货地址快照
    
    @Column(name = "invoice_snapshot", columnDefinition = "jsonb")
    private String invoiceSnapshot; // 发票信息快照
    
    @Column(name = "remark")
    private String remark; // 订单备注
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt; // 支付时间
    
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt; // 发货时间
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt; // 完成时间
    
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt; // 取消时间
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();
    
    // 业务方法
    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }
    
    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }
    
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(this);
        history.setStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        statusHistory.add(history);
    }
    
    public void markAsPaid() {
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.paidAt = LocalDateTime.now();
        updateStatus(OrderStatus.PAID);
    }
    
    public void markAsShipped() {
        this.shippingStatus = ShippingStatus.SHIPPED;
        this.shippedAt = LocalDateTime.now();
        updateStatus(OrderStatus.SHIPPED);
    }
    
    public void markAsCompleted() {
        this.shippingStatus = ShippingStatus.DELIVERED;
        this.completedAt = LocalDateTime.now();
        updateStatus(OrderStatus.COMPLETED);
    }
    
    public void cancel() {
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        updateStatus(OrderStatus.CANCELED);
    }
    
    public boolean canCancel() {
        return status == OrderStatus.PENDING || status == OrderStatus.WAIT_PAYMENT;
    }
    
    public boolean canPay() {
        return status == OrderStatus.PENDING && paymentStatus == PaymentStatus.INIT;
    }
    
    // Getters and Setters
    public String getOrderNumber() {
        return orderNumber;
    }
    
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
    public Long getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public Long getDiscountAmount() {
        return discountAmount;
    }
    
    public void setDiscountAmount(Long discountAmount) {
        this.discountAmount = discountAmount;
    }
    
    public Long getPayAmount() {
        return payAmount;
    }
    
    public void setPayAmount(Long payAmount) {
        this.payAmount = payAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }
    
    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
    
    public ShippingStatus getShippingStatus() {
        return shippingStatus;
    }
    
    public void setShippingStatus(ShippingStatus shippingStatus) {
        this.shippingStatus = shippingStatus;
    }
    
    public String getAddressSnapshot() {
        return addressSnapshot;
    }
    
    public void setAddressSnapshot(String addressSnapshot) {
        this.addressSnapshot = addressSnapshot;
    }
    
    public String getInvoiceSnapshot() {
        return invoiceSnapshot;
    }
    
    public void setInvoiceSnapshot(String invoiceSnapshot) {
        this.invoiceSnapshot = invoiceSnapshot;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
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
    
    public LocalDateTime getShippedAt() {
        return shippedAt;
    }
    
    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }
    
    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }
    
    public List<OrderItem> getOrderItems() {
        return orderItems;
    }
    
    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }
    
    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }
    
    public void setStatusHistory(List<OrderStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }
}
