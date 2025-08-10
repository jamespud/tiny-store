package com.github.spud.tinystore.domain.ordering;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.security.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 订单状态历史实体
 * 对应 ordering.order_status_history 表
 */
@Entity
@Table(name = "order_status_history", schema = "ordering")
public class OrderStatusHistory extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    
    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy; // 操作人
    
    @Column(name = "note")
    private String note; // 备注
    
    // 业务方法
    public boolean isStatusChange() {
        return order != null && status != null;
    }
    
    public String getStatusDescription() {
        return status != null ? status.getDescription() : "";
    }
    
    public String getOperatorName() {
        return changedBy != null ? changedBy.getUsername() : "系统";
    }
    
    // Getters and Setters
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getChangedAt() {
        return changedAt;
    }
    
    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
    
    public User getChangedBy() {
        return changedBy;
    }
    
    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
}
