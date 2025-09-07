package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.application.service.pricing.PricingPipelineAppService;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingSummaryView;
import com.github.spud.tinystore.order.infrastructure.acl.client.ProductClient;
import com.github.spud.tinystore.order.interfaces.dto.Settlement;
import com.github.spud.tinystore.order.interfaces.vo.SettlementPreviewVO.Snapshot;
import com.github.spud.tinystore.order.interfaces.vo.SettlementPreviewVO.Summary;
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
public class OrderPriceCalculationService {

	@Autowired
	private ProductClient productClient;

	@Autowired
	private PricingPipelineAppService pricingPipeline;

	public Object calculatePrice(List<Object> products, List<Object> coupons) {
		return new Object();
	}

	public Object calculatePrice(List<Object> products) {
		return new Object();
	}

	/**
	 * 计算订单汇总金额
	 */
	public Summary calculateSummary(Settlement settlement) {
		productClient.replenishProductInformation(settlement);

		PricingContext ctx = new PricingContext();
		ctx.setItems(settlement.getItems());
		ctx.setProductMap(settlement.productMap);
		PricingSummaryView view = pricingPipeline.price(ctx);
		return new Summary(view.getItemsTotal(), view.getDiscountTotal(), view.getShippingFee(),
			view.getTaxTotal(), view.getPayable());
	}

	/**
	 * 生成商品快照
	 */
	public List<Snapshot> generateSnapshots(Settlement settlement) {
		productClient.replenishProductInformation(settlement);

		PricingContext ctx = new PricingContext();
		ctx.setItems(settlement.getItems());
		ctx.setProductMap(settlement.productMap);
		pricingPipeline.price(ctx);
		return ctx.getLines().stream()
			.map(l -> new Snapshot(
				Long.parseLong(l.getShopId()),
				Long.parseLong(l.getSkuId()),
				settlement.productMap.get(Long.parseLong(l.getSkuId())) != null ? settlement.productMap.get(
					Long.parseLong(l.getSkuId())).getTitle() : "",
				settlement.productMap.get(Long.parseLong(l.getSkuId())) != null ? settlement.productMap.get(
					Long.parseLong(l.getSkuId())).getSpec() : "",
				l.getUnitPriceCents(),
				l.getQuantity(),
				l.getNetLineTotalCents()))
			.toList();
	}
}
