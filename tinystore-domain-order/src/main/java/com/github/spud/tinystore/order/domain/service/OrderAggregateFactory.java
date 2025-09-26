package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand;
import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand.ProductDto;
import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand.ProductItem;
import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.model.*;
import com.github.spud.tinystore.order.domain.model.line.LineItem;
import com.github.spud.tinystore.order.domain.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.status.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Service
public class OrderAggregateFactory {

	@Autowired
	private UserService userService;

	public OrderAggregate createOrder(OrderAggregate.CreateOrderArgs args) {
		OrderAggregate aggregate = null;
		return aggregate;
	}

	public OrderAggregate createOrder(String userId, String addressId, List<ProductItem> items, List<Product> products, List<Coupon> coupons) {
		Buyer buyer = userService.getBuyerById(userId);
		Address address = userService.getAddressById(addressId);

		Map<Product, Integer> productQty = toProductMap(items, products);

		// 1) 按店铺拆分
		Map<String, Map<Product, Integer>> byShop = productQty.entrySet().stream()
			.collect(Collectors.groupingBy(e -> e.getKey().shopId(),
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

		// 2) 为每个店铺生成子订单（含行项、费用、按比例分摊的优惠）
		List<SubOrder> subOrders = new ArrayList<>();
		for (Map.Entry<String, Map<Product, Integer>> entry : byShop.entrySet()) {
			String shopId = entry.getKey();
			Map<Product, Integer> shopProducts = entry.getValue();

			// 2.1 行项：单行包含该店铺所有商品
			List<LineItem> lines = buildOrderLines(shopProducts);

			// 2.2 商品小计
			Money goodsTotal = sumLines(lines);

			// 2.3 计算附加费用（如运费、税费），此处占位：无附加费用
			List<ChargeItem> chargeItems = List.of();
			Money extraTotal = Money.zeroLike(goodsTotal);

			// 2.4 店铺可用优惠（筛选作用域为该店铺或全店/全场的券）
			List<Coupon> shopCoupons = filterCouponsForShop(coupons, shopId);

			// 2.5 按行小计占比做优惠分摊
			List<DiscountAllocation> allocations = allocateDiscountsProRata(shopId, lines, shopCoupons, goodsTotal);

			Money discountTotal = sumAllocations(allocations, goodsTotal);
			Money payable = goodsTotalSubtractDiscountAddExtra(goodsTotal, discountTotal, extraTotal);

			// 2.6 构建子订单（修复构造参数顺序与类型）
			SubOrder sub = new SubOrder(
				"", // subOrderId 由持久化生成
				shopId,
				lines, // List<OrderLine>
				chargeItems, // List<ChargeItem>
				address);
			subOrders.add(sub);
		}

		// 3) 构建父订单
		return OrderAggregate.builder()
			.buyer(buyer)
			.subOrders(subOrders)
			.coupons(coupons)
			.discounts(null)
			.address(address)
			.build();
	}

	// 预览下单：构建父订单数据并完成店铺级拆单、费用与优惠的占位计算
	public OrderAggregate createOrder(PreviewOrderCommand cmd, List<Product> products, List<Coupon> coupons) {
		return createOrder(cmd.getUserId(), cmd.getAddressId(), cmd.getProductItems(), products, coupons);
	}

	public OrderAggregate createOrder(SubmitOrderCommand cmd, List<Product> products, List<Coupon> coupons) {
		return createOrder(cmd.getUserId(), cmd.getAddressId(), cmd.getProductItems(), products, coupons);
	}

	private Map<Product, Integer> toProductMap(List<ProductItem> items, List<Product> products) {
		Map<String, Integer> quantityMap = items.stream()
			.flatMap(pi -> pi.products().stream())
			.collect(Collectors.toMap(ProductDto::skuId, ProductDto::quantity, Integer::sum));
		// 防空处理：若缺失数量，置 0，避免 NPE
		return products.stream().collect(Collectors.toMap(
			Function.identity(),
			p -> quantityMap.getOrDefault(p.skuId(), 0)));
	}

	public SubOrder createOrderLine(String userId, String shopId, Map<Product, Integer> products,
	                                Money total, Money discountIgnored, Money payable, List<ChargeItem> chargeItems, Address address) {
		// 单行多商品：与预览保持一致
		List<OrderLine> lines = buildOrderLines(products);
		List<DiscountAllocation> allocations = List.of(); // 若有券，后续在价格流水线中完成分摊

		return new SubOrder(
			"",
			shopId,
			lines,
			chargeItems != null ? chargeItems : List.of(),
			allocations,
			total,
			payable,
			FulfillmentStatus.NONE,
			OrderStatus.CREATED,
			PaymentStatus.NONE,
			AfterSaleStatus.NONE,
			address);
	}

	// ===== 辅助方法 =====

	// 按产品与数量构建行项：单行包含店铺所有商品
	private List<LineItem> buildOrderLines(Map<Product, Integer> products) {
		// 单行：包含该店铺的所有商品，products 中剔除数量<=0
		Map<Product, Integer> lineProducts = products.entrySet().stream()
			.filter(e -> e.getValue() != null && e.getValue() > 0)
			.sorted(Comparator.comparing(e -> e.getKey().skuId()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum,
				java.util.LinkedHashMap::new));

		if (lineProducts.isEmpty()) {
			return List.of();
		}

		Money total = lineSubtotalFromMap(lineProducts);
		Money discount = Money.zeroLike(total);
		Money payable = total;
		LineItem line = new LineItem(lineProducts, total, discount, payable);
		return List.of(line);
	}

	// 商品小计
	private Money sumLines(List<LineItem> lines) {
		if (lines == null || lines.isEmpty())
			return Money.zero();
		return lines.stream().map(LineItem::total).reduce(Money::add).orElse(Money.zero());
	}

	// 单行小计
	private Money lineSubtotal(LineItem line) {
		if (line == null)
			return Money.zero();
		if (line.total() != null)
			return line.total();
		// 兜底：按行内商品计算
		return lineSubtotalFromMap(line.products());
	}

	// 店铺券筛选（按需替换判定逻辑）
	private List<Coupon> filterCouponsForShop(List<Coupon> coupons, String shopId) {
		if (coupons == null || coupons.isEmpty())
			return List.of();
		// 全店/全场均可用
		return coupons;
	}

	// 按比例分摊优惠到行项 - 最大余数法
	private List<DiscountAllocation> allocateDiscountsProRata(String shopId, List<OrderLine> lines,
	                                                          List<Coupon> coupons, Money goodsTotal) {
		if (coupons == null || coupons.isEmpty() || lines.isEmpty())
			return List.of();

		long totalAmount = coupons.stream().map(Coupon::amount).mapToLong(Money::amount).sum();
		// 折扣不超过商品小计
		totalAmount = Math.min(totalAmount, goodsTotal.amount());
		if (totalAmount <= 0 || goodsTotal.amount() <= 0)
			return List.of();

		// 计算每行分摊（使用整数除法与最大余数回填）
		int n = lines.size();
		long[] bases = new long[n];
		long[] remainders = new long[n];
		long sumBase = 0;

		for (int i = 0; i < n; i++) {
			Money sub = lineSubtotal(lines.get(i));
			long numerator = sub.amount() * totalAmount; // 以分为单位
			long q = numerator / goodsTotal.amount();
			long r = numerator % goodsTotal.amount();
			bases[i] = q;
			remainders[i] = r;
			sumBase += q;
		}

		long left = totalAmount - sumBase;
		// 选择余数最大的若干行 +1 分
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < n; i++)
			idx.add(i);
		idx.sort((a, b) -> Long.compare(remainders[b], remainders[a]));
		for (int i = 0; i < left; i++) {
			bases[idx.get(i)] += 1;
		}

		// 构造 DiscountAllocation
		List<DiscountAllocation> allocations = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			OrderLine line = lines.get(i);
			String firstSkuId = firstSkuId(line);
			String targetId = shopId + "#" + firstSkuId + "#" + i;
			allocations.add(new DiscountAllocation("COUPON", Money.ofCents(bases[i], goodsTotal.currency()), targetId));
		}
		return allocations;
	}

	private Money sumAllocations(List<DiscountAllocation> allocations, Money baseline) {
		if (allocations == null || allocations.isEmpty())
			return Money.zeroLike(baseline);
		return allocations.stream().map(DiscountAllocation::amount).reduce(Money.zeroLike(baseline), Money::add);
	}

	private Money goodsTotalSubtractDiscountAddExtra(Money goodsTotal, Money discount, Money extra) {
		return goodsTotal.subtract(discount).add(extra);
	}

	private Money lineSubtotalFromMap(Map<Product, Integer> products) {
		if (products == null || products.isEmpty())
			return Money.zero();
		Money total = null;
		for (Map.Entry<Product, Integer> e : products.entrySet()) {
			Product p = e.getKey();
			int qty = e.getValue() == null ? 0 : e.getValue();
			if (qty <= 0)
				continue;
			Money sub = p.unitPrice().multiply(qty);
			total = (total == null) ? sub : total.add(sub);
		}
		return total == null ? Money.zero() : total;
	}

	private String firstSkuId(OrderLine line) {
		return line.products().keySet().stream()
			.sorted(Comparator.comparing(Product::skuId))
			.map(Product::skuId)
			.findFirst()
			.orElse("-");
	}
}
