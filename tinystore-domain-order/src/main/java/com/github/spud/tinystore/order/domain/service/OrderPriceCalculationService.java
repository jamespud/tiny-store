package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.application.service.pricing.PricingPipelineAppService;
import com.github.spud.tinystore.order.domain.enums.ChargeType;
import com.github.spud.tinystore.order.domain.model.Address;
import com.github.spud.tinystore.order.domain.model.ChargeItem;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.PricingSummary;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.infrastructure.acl.ProductClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
	
	@Autowired
	private OrderFactory orderFactory;

	/**
	 * 计算订单价格 // 1. 计算店铺商品总价 // 3. 计算税费和运费 // 4. 计算最终支付金额 // 5. 更新订单的价格明细 // 6. 返回更新后的订单对象
	 *
	 * @param order
	 * @return
	 */
	public Order calculatePrice(Order order) {
		if (order.isCalculated()) {
			return order;
		}
		// 1. 计算店铺商品总价
		HashMap<String, Map<Product, Integer>> shopProducts = new HashMap<>();
		order.getProducts()
			.forEach(((product, quantity) -> shopProducts.put(product.shopId(),
				shopProducts.getOrDefault(product.shopId(), new HashMap<>())).put(product, quantity)));
		HashMap<String, Money> shopTotal = new HashMap<>();
		order.getProducts().forEach((product, quantity) -> {
			String shopId = product.shopId();
			Money subTotal = product.unitPrice().multiply(quantity);
			shopTotal.put(shopId, shopTotal.getOrDefault(shopId, Money.zero()).add(subTotal));
		});
		Money itemTotal = Money.zero();
		shopTotal.forEach((s, money) -> itemTotal.add(money));
		// 2. TODO: 应用优惠券和折扣
		Money discountTotal = Money.zero();
		Money couponTotal = Money.zero();
		order.setCoupons(List.of());
		order.setDiscounts(List.of());
		order.setCouponAllocations(List.of());
		order.setDiscountAllocations(List.of());
		
		Money chargeTotal = Money.zero();
		for (Entry<String, Map<Product, Integer>> entry : shopProducts.entrySet()) {
			List<ChargeItem> chargeItems = calculateLineChargeItem(entry.getValue(), order.getAddress());
			OrderLine orderLine = orderFactory.createOrderLine(order.getBuyer().userId(), entry.getKey(),
				entry.getValue(),
				shopTotal.get(entry.getKey()), discountTotal.add(couponTotal),
				shopTotal.get(entry.getKey()).subtract(discountTotal),
				chargeItems, order.getAddress());
			chargeItems.forEach(c -> chargeTotal.add(c.amount()));
			order.getLines().add(orderLine);
		}
		// TODO:
		order.setPricingSummary(new PricingSummary(itemTotal.add(chargeTotal), discountTotal.add(couponTotal),
			List.of(), itemTotal.add(chargeTotal).subtract(discountTotal.add(couponTotal))));

		return order;
	}


	/**
	 * 计算订单行的附加费用，如运费和税费
	 *
	 * @param products 商品及数量
	 * @param address  收货地址
	 * @return 附加费用列表
	 */
	public List<ChargeItem> calculateLineChargeItem(Map<Product, Integer> products, Address address) {
		ChargeItem shipping = new ChargeItem(ChargeType.SHIPPING, Money.zero(), "运费");
		ChargeItem tax = new ChargeItem(ChargeType.TAX, Money.zero(), "税费");
		return List.of(shipping, tax);
	}


}
