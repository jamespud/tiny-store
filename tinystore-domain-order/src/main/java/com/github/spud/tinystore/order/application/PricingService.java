package com.github.spud.tinystore.order.application;

import com.github.spud.tinystore.order.api.dto.Product;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.domain.client.ProductDomainService;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Snapshot;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Summary;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 价格计算服务
 *
 * @author Spud
 * @date 2025/8/16
 */
@Service
public class PricingService {

	@Autowired
	private ProductDomainService productDomainService;

	/**
	 * 计算订单汇总金额
	 */
	public Summary calculateSummary(Settlement settlement) {
		productDomainService.replenishProductInformation(settlement);

		long total = recalculatePrice(settlement);
		long discount = calculateDiscount(settlement);
		long shippingFee = calculateShippingFee(settlement);
		long tax = calculateTax(settlement);
		long payable = total + shippingFee + tax - discount;

		return new Summary(total, discount, shippingFee, tax, payable);
	}

	/**
	 * 生成商品快照
	 */
	public List<Snapshot> generateSnapshots(Settlement settlement) {
		productDomainService.replenishProductInformation(settlement);

		return settlement.productMap.values().stream()
			.map(Product::toLineSnapshot)
			.toList();
	}

	/**
	 * 基于Settlement重新计算价格 - 用于提交阶段验证
	 */
	public Summary recalculateSummary(Settlement settlement) {
		productDomainService.replenishProductInformation(settlement);

		long total = recalculatePrice(settlement);
		long discount = calculateDiscount(settlement);
		long shippingFee = calculateShippingFee(settlement);
		long tax = calculateTax(settlement);
		long payable = total + shippingFee + tax - discount;

		return new Summary(total, discount, shippingFee, tax, payable);
	}

	private long recalculatePrice(Settlement settlement) {
		double sum = settlement.getItems().stream()
			.mapToDouble(
				item -> settlement.productMap.get(item.getProductId()).getUnitPrice() * item.getAmount())
			.sum();
		return Math.round(sum);
	}

	private long calculateDiscount(Settlement settlement) {
		// TODO: 实现优惠券计算逻辑
		return 0;
	}

	private long calculateShippingFee(Settlement settlement) {
		// TODO: 实现动态运费计算
		return 12;
	}

	private long calculateTax(Settlement settlement) {
		// TODO: 实现税费计算
		return 0;
	}
}
