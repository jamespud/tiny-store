package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.application.command.CreateOrderCommand;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand.ProductDto;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand.ProductItem;
import com.github.spud.tinystore.order.domain.enums.BuyerType;
import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.model.Address;
import com.github.spud.tinystore.order.domain.model.Buyer;
import com.github.spud.tinystore.order.domain.model.ChargeItem;
import com.github.spud.tinystore.order.domain.model.Coupon;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.domain.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.status.PaymentStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Service
public class OrderFactory {

	public Order createOrder(PreviewOrderCommand cmd, List<Product> products, List<Coupon> coupons) {
		Map<Product, Integer> collect = toProductMap(cmd.getProducts(), products);
		// TODO: 订单拆分

		return Order.builder()
			.buyer(new Buyer(cmd.getUserId(), BuyerType.NORMAL, 1))
			.products(collect)
			.coupons(coupons)
			.discounts(null)
			.deviceId(cmd.getDeviceId())
			.address(new Address("", "", "", "", "", "", "", ""))
			.calculated(false)
			.build();
	}

	public Order createOrder(CreateOrderCommand cmd, List<Product> products, List<Coupon> coupons) {
		Map<Product, Integer> collect = toProductMap(cmd.getProducts(), products);

		return Order.builder()
			.buyer(new Buyer(cmd.getUserId(), BuyerType.NORMAL, 1))
			.products(collect)
			.coupons(coupons)
			.discounts(null)
			.deviceId(cmd.getDeviceId())
			.address(new Address("", "", "", "", "", "", "", ""))
			.build();
	}

	private Map<Product, Integer> toProductMap(List<ProductItem> items, List<Product> products) {
		Map<String, Integer> quantityMap = items.stream()
			.flatMap(pi -> pi.products().stream())
			.collect(Collectors.toMap(ProductDto::skuId, ProductDto::quantity));
		return products.stream()
			.collect(Collectors.toMap(p -> p, p -> quantityMap.get(p.skuId())));
	}

	public SubOrder createOrderLine(String userId, String shopId, Map<Product, Integer> products,
		Money total, Money discount, Money payable, List<ChargeItem> chargeItems, Address address) {
		return new SubOrder("", userId, shopId, products, total, discount, payable, chargeItems,
			OrderStatus.CREATED, PaymentStatus.UNPAID, FulfillmentStatus.NONE, AfterSaleStatus.NONE, address);
	}
	
}
