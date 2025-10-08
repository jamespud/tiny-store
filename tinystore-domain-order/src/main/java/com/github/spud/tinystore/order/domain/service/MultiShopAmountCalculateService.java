package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.domain.model.SubOrderItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 多店铺订单金额计算领域服务（核心处理跨店优惠分摊）
 */
@Service
@RequiredArgsConstructor
public class MultiShopAmountCalculateService {

	/**
	 * 平台优惠券总优惠按“子订单商品总价占比”分摊到每个子订单 例：总平台优惠10元，子订单A商品总价90元，子订单B商品总价110元 → A分摊4.5元，B分摊5.5元
	 */
	public List<SubOrder> allocatePlatformDiscount(List<SubOrder> subOrders,
		Money platformDiscountTotal) {
		// 1. 计算所有子订单商品总价之和（用于计算占比）
		Money allSubGoodsTotal = subOrders.stream()
			.map(SubOrder::getSubGoodsTotal)
			.reduce(Money.of(0), Money::add);

		// 2. 若总商品总价为0或无平台优惠，直接返回
		if (allSubGoodsTotal.amount() <= 0 || platformDiscountTotal.amount() <= 0) {
			subOrders.forEach(sub -> {
				// 子订单实付金额 = 商品总价 - 商家优惠 + 运费
				Money subPayAmount = sub.getSubGoodsTotal()
					.subtract(sub.getSubMerchantDiscount())
					.add(sub.getSubFreight());
				sub.setSubPayAmount(subPayAmount);
			});
			return subOrders;
		}

		// 3. 按占比分摊平台优惠到每个子订单（保留2位小数，处理尾差）
		List<SubOrder> subOrdersWithAllocated = subOrders.stream()
			.map(sub -> {
				// 3.1 计算当前子订单占比（子订单商品总价 / 所有子订单商品总价）
				long subRatio = sub.getSubGoodsTotal().amount() / allSubGoodsTotal.amount();

				// 3.2 计算当前子订单分摊的平台优惠（占比 × 总平台优惠）
				Money subPlatformDiscount = Money.of(
					subRatio * platformDiscountTotal.amount()
				);

				// 3.3 计算子订单实付金额（商品总价 - 商家优惠 - 平台分摊优惠 + 运费）
				Money subPayAmount = sub.getSubGoodsTotal()
					.subtract(sub.getSubMerchantDiscount())
					.subtract(subPlatformDiscount)
					.add(sub.getSubFreight());
				// 确保实付金额≥0
				if (subPayAmount.amount() < 0) {
					subPayAmount = Money.of(0);
				}

				// 3.4 分摊平台优惠到子订单的每个SKU明细（按明细小计占比）
				List<SubOrderItem> itemsWithDiscount = allocateDiscountToSubItems(
					sub.getSubItems(), subPlatformDiscount
				);

				// 3.5 更新子订单信息
				return sub.toBuilder()
					.subPlatformDiscount(subPlatformDiscount)
					.subPayAmount(subPayAmount)
					.subItems(itemsWithDiscount)
					.build();
			})
			.collect(Collectors.toList());

		// 4. 处理尾差（若分摊后总优惠≠原总优惠，调整最后一个子订单）
		Money allocatedTotal = subOrdersWithAllocated.stream()
			.map(SubOrder::getSubPlatformDiscount)
			.reduce(Money.of(0), Money::add);
		long diff = platformDiscountTotal.amount() - allocatedTotal.amount();
		if (diff != 0) {
			SubOrder lastSub = subOrdersWithAllocated.get(subOrdersWithAllocated.size() - 1);
			// 调整最后一个子订单的平台优惠
			Money adjustedDiscount = Money.of(lastSub.getSubPlatformDiscount().amount() + (diff));
			// 调整最后一个子订单的实付金额
			Money adjustedPayAmount = lastSub.getSubGoodsTotal()
				.subtract(lastSub.getSubMerchantDiscount())
				.subtract(adjustedDiscount)
				.add(lastSub.getSubFreight());
			if (adjustedPayAmount.amount() < 0) {
				adjustedPayAmount = Money.of(0);
			}
			// 更新最后一个子订单
			subOrdersWithAllocated.set(subOrdersWithAllocated.size() - 1, lastSub.toBuilder()
				.subPlatformDiscount(adjustedDiscount)
				.subPayAmount(adjustedPayAmount)
				.build());
		}

		return subOrdersWithAllocated;
	}

	/**
	 * 子订单的平台优惠按“明细小计占比”分摊到每个SKU明细
	 */
	private List<SubOrderItem> allocateDiscountToSubItems(List<SubOrderItem> items,
		Money subPlatformDiscount) {
		// 1. 计算当前子订单所有明细小计之和
		Money itemTotalSum = items.stream()
			.map(SubOrderItem::getItemTotalPrice)
			.reduce(Money.of(0), Money::add);
		if (itemTotalSum.amount() <= 0) {
			return items;
		}

		// 2. 按明细小计占比分摊优惠
		List<SubOrderItem> itemsWithDiscount = items.stream()
			.map(item -> {
				double itemRatio = (double) item.getItemTotalPrice().amount() / itemTotalSum.amount();
				// TODO: 这里可能会有精度问题，后续优化
				Money itemDiscount = Money.of(
					(long) (itemRatio * subPlatformDiscount.amount())
				);
				return item.toBuilder()
					.itemPlatformDiscount(itemDiscount)
					.build();
			})
			.collect(Collectors.toList());

		// 3. 处理明细尾差
		Money itemDiscountTotal = itemsWithDiscount.stream()
			.map(SubOrderItem::getItemPlatformDiscount)
			.reduce(Money.of(0), Money::add);
		long itemDiff = subPlatformDiscount.amount() -itemDiscountTotal.amount();
		if (itemDiff != 0) {
			SubOrderItem lastItem = itemsWithDiscount.get(itemsWithDiscount.size() - 1);
			Money adjustedItemDiscount = Money.of(
				lastItem.getItemPlatformDiscount().amount() + itemDiff);
			itemsWithDiscount.set(itemsWithDiscount.size() - 1, lastItem.toBuilder()
				.itemPlatformDiscount(adjustedItemDiscount)
				.build());
		}

		return itemsWithDiscount;
	}
}