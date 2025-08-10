package com.github.spud.tinystore.domain.ordering;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.catalog.ProductSku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 购物车项实体
 * 对应 ordering.cart_items 表
 */
@Entity
@Table(name = "cart_items", schema = "ordering")
public class CartItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private ProductSku sku;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot; // 价格快照（分）
    
    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
    
    // 业务方法
    public long getSubtotal() {
        return priceSnapshot * quantity;
    }
    
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }
        this.quantity = newQuantity;
    }
    
    // Getters and Setters
    public Cart getCart() {
        return cart;
    }
    
    public void setCart(Cart cart) {
        this.cart = cart;
    }
    
    public ProductSku getSku() {
        return sku;
    }
    
    public void setSku(ProductSku sku) {
        this.sku = sku;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public Long getPriceSnapshot() {
        return priceSnapshot;
    }
    
    public void setPriceSnapshot(Long priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }
    
    public LocalDateTime getAddedAt() {
        return addedAt;
    }
    
    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
