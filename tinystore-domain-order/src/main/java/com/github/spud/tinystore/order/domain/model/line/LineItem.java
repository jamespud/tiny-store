package com.github.spud.tinystore.order.domain.model.line;

import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import com.github.spud.tinystore.order.domain.model.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单行实体 - 权威数据来源
 * 
 * 作为订单明细的唯一事实来源，支持简单行与组合套装
 * 所有的数量、价格、分摊等计算以此为准
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {
    
    /**
     * 行标识符
     */
    private LineId lineId;
    
    /**
     * 行类型：简单行/组合行/组件行
     */
    private LineType type;
    
    /**
     * SKU快照（COMPOSITE类型可为null）
     */
    private SkuSnapshot skuSnapshot;
    
    /**
     * 数量（COMPOSITE类型为0）
     */
    private int quantity;
    
    /**
     * 单价（来自快照或组合定价）
     */
    private Money unitPrice;
    
    /**
     * 行总额 = unitPrice * quantity
     */
    private Money lineTotal;
    
    /**
     * 行级折扣分摊金额 (促销&优惠券)
     */
    private Money lineDiscount;
    
    /**
     * 行应付金额 = lineTotal - lineDiscount
     */
    private Money linePayable;
    
    /**
     * 套装分组ID（用于关联组合行与组件行）
     */
    private String bundleGroupId;
    
    /**
     * 父组合行ID（组件行指向其组合行）
     */
    private String parentCompositeId;
    
    /**
     * 创建简单行
     * 
     * @param lineId 行ID
     * @param skuSnapshot SKU快照
     * @param quantity 数量
     * @return 简单行实例
     */
    public static LineItem createSimple(LineId lineId, SkuSnapshot skuSnapshot, int quantity) {
        validateQuantity(quantity);
        
        Money unitPrice = skuSnapshot.unitPrice();
        Money lineTotal = unitPrice.multiply(quantity);
        
        return LineItem.builder()
                .lineId(lineId)
                .type(LineType.SIMPLE)
                .skuSnapshot(skuSnapshot)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(lineTotal)
                .lineDiscount(Money.zero())
                .linePayable(lineTotal)
                .build();
    }
    
    /**
     * 创建组合行（套装容器）
     * 
     * @param lineId 行ID
     * @param compositePrice 组合价
     * @param bundleGroupId 套装分组ID
     * @return 组合行实例
     */
    public static LineItem createComposite(LineId lineId, Money compositePrice, String bundleGroupId) {
        return LineItem.builder()
                .lineId(lineId)
                .type(LineType.COMPOSITE)
                .skuSnapshot(null) // 组合行不对应具体SKU
                .quantity(0) // 组合行数量为0
                .unitPrice(compositePrice)
                .lineTotal(compositePrice)
                .lineDiscount(Money.zero())
                .linePayable(compositePrice)
                .bundleGroupId(bundleGroupId)
                .build();
    }
    
    /**
     * 创建组件行（套装中的具体SKU）
     * 
     * @param lineId 行ID
     * @param skuSnapshot SKU快照
     * @param quantity 数量
     * @param parentCompositeId 父组合行ID
     * @param bundleGroupId 套装分组ID
     * @return 组件行实例
     */
    public static LineItem createComponent(LineId lineId, SkuSnapshot skuSnapshot, int quantity, 
                                          String parentCompositeId, String bundleGroupId) {
        validateQuantity(quantity);
        
        Money unitPrice = skuSnapshot.unitPrice();
        Money lineTotal = unitPrice.multiply(quantity);
        
        return LineItem.builder()
                .lineId(lineId)
                .type(LineType.COMPONENT)
                .skuSnapshot(skuSnapshot)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineTotal(lineTotal)
                .lineDiscount(Money.zero())
                .linePayable(lineTotal)
                .parentCompositeId(parentCompositeId)
                .bundleGroupId(bundleGroupId)
                .build();
    }
    
    /**
     * 应用行级折扣
     * 
     * @param discountAmount 折扣金额
     */
    public void applyDiscount(Money discountAmount) {
        if (discountAmount == null) {
            throw new OrderDomainException("Discount amount cannot be null", "INVALID_DISCOUNT");
        }
        
        this.lineDiscount = this.lineDiscount.add(discountAmount);
        this.linePayable = this.lineTotal.subtract(this.lineDiscount);
        
        if (!this.linePayable.nonNegative()) {
            throw new OrderDomainException("Line payable cannot be negative", "NEGATIVE_PAYABLE");
        }
    }
    
    /**
     * 重新计算行金额（用于价格变更场景）
     */
    public void recalculate() {
        if (this.type == LineType.COMPOSITE) {
            // 组合行的lineTotal就是组合价
            this.lineTotal = this.unitPrice;
        } else {
            // 简单行和组件行按数量计算
            this.lineTotal = this.unitPrice.multiply(this.quantity);
        }
        
        this.linePayable = this.lineTotal.subtract(this.lineDiscount);
        
        if (!this.linePayable.nonNegative()) {
            throw new OrderDomainException("Line payable cannot be negative after recalculation", "NEGATIVE_PAYABLE");
        }
    }
    
    /**
     * 校验数量有效性
     */
    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new OrderDomainException("Quantity must be positive", "INVALID_QUANTITY");
        }
    }
    
    /**
     * 获取SKU ID（如果是组合行则返回null）
     */
    public String getSkuId() {
        return skuSnapshot != null ? skuSnapshot.skuId() : null;
    }
    
    /**
     * 是否为套装相关行（组合行或组件行）
     */
    public boolean isBundleRelated() {
        return bundleGroupId != null && !bundleGroupId.trim().isEmpty();
    }
    
    /**
     * 是否为组合行
     */
    public boolean isComposite() {
        return type == LineType.COMPOSITE;
    }
    
    /**
     * 是否为组件行
     */
    public boolean isComponent() {
        return type == LineType.COMPONENT;
    }
    
    /**
     * 是否为简单行
     */
    public boolean isSimple() {
        return type == LineType.SIMPLE;
    }
}