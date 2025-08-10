package com.github.spud.tinystore.domain.payment;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.ordering.OrderItem;
import jakarta.persistence.*;

/**
 * 退款项实体
 * 对应 payment.refund_items 表
 */
@Entity
@Table(name = "refund_items", schema = "payment")
public class RefundItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "amount", nullable = false)
    private Long amount; // 退款金额（分）
    
    // 业务方法
    public boolean isValidQuantity() {
        return quantity != null && quantity > 0 && 
               orderItem != null && quantity <= orderItem.getQuantity();
    }
    
    public boolean isValidAmount() {
        return amount != null && amount > 0 && 
               orderItem != null && amount <= orderItem.getTotalAmount();
    }
    
    public long getUnitRefundAmount() {
        if (quantity == null || quantity <= 0) {
            return 0;
        }
        return amount / quantity;
    }
    
    public String getFormattedAmount() {
        return String.format("%.2f", amount / 100.0);
    }
    
    // Getters and Setters
    public Refund getRefund() {
        return refund;
    }
    
    public void setRefund(Refund refund) {
        this.refund = refund;
    }
    
    public OrderItem getOrderItem() {
        return orderItem;
    }
    
    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public Long getAmount() {
        return amount;
    }
    
    public void setAmount(Long amount) {
        this.amount = amount;
    }
}
