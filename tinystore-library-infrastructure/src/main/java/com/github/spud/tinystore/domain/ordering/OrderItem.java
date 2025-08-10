package com.github.spud.tinystore.domain.ordering;

import com.github.spud.tinystore.domain.BaseEntity;
import com.github.spud.tinystore.domain.catalog.Product;
import com.github.spud.tinystore.domain.catalog.ProductSku;
import jakarta.persistence.*;

/**
 * 订单项实体
 * 对应 ordering.order_items 表
 */
@Entity
@Table(name = "order_items", schema = "ordering")
public class OrderItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private ProductSku sku;
    
    @Column(name = "sku_title", nullable = false)
    private String skuTitle; // SKU名称快照
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "price", nullable = false)
    private Long price; // 单价（分）
    
    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L; // 优惠金额（分）
    
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount; // 小计金额（分）
    
    @Column(name = "sku_snapshot", columnDefinition = "jsonb")
    private String skuSnapshot; // SKU属性快照
    
    // 业务方法
    public long calculateSubtotal() {
        return price * quantity;
    }
    
    public long calculateFinalAmount() {
        return calculateSubtotal() - discountAmount;
    }
    
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }
        this.quantity = newQuantity;
        this.totalAmount = calculateFinalAmount();
    }
    
    public void applyDiscount(long discountAmount) {
        if (discountAmount < 0) {
            throw new IllegalArgumentException("优惠金额不能为负数");
        }
        if (discountAmount > calculateSubtotal()) {
            throw new IllegalArgumentException("优惠金额不能超过小计金额");
        }
        this.discountAmount = discountAmount;
        this.totalAmount = calculateFinalAmount();
    }
    
    // Getters and Setters
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public Product getProduct() {
        return product;
    }
    
    public void setProduct(Product product) {
        this.product = product;
    }
    
    public ProductSku getSku() {
        return sku;
    }
    
    public void setSku(ProductSku sku) {
        this.sku = sku;
    }
    
    public String getSkuTitle() {
        return skuTitle;
    }
    
    public void setSkuTitle(String skuTitle) {
        this.skuTitle = skuTitle;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public Long getPrice() {
        return price;
    }
    
    public void setPrice(Long price) {
        this.price = price;
    }
    
    public Long getDiscountAmount() {
        return discountAmount;
    }
    
    public void setDiscountAmount(Long discountAmount) {
        this.discountAmount = discountAmount;
    }
    
    public Long getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public String getSkuSnapshot() {
        return skuSnapshot;
    }
    
    public void setSkuSnapshot(String skuSnapshot) {
        this.skuSnapshot = skuSnapshot;
    }
}
