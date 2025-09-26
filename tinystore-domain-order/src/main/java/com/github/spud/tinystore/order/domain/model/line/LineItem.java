package com.github.spud.tinystore.order.domain.model.line;

import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import com.github.spud.tinystore.order.domain.model.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单行实体 - 权威数据来源
 * <p>
 * 作为订单明细的唯一事实来源，支持简单行与组合套装
 * 所有的数量、价格、分摊等计算以此为准
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {

	/**
	 * SKU快照
	 */
	private SkuSnapshot skuSnapshot;

	/**
	 * 数量
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

		// 简单行和组件行按数量计算
		this.lineTotal = this.unitPrice.multiply(this.quantity);


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

}