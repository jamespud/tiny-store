package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.user.*;
import com.github.spud.tinystore.order.application.result.SubmitOrderResult;
import com.github.spud.tinystore.order.domain.model.Buyer;
import com.github.spud.tinystore.order.domain.model.Discount;
import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.domain.service.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserOrderApplicationService {

	private final DiscountService discountService;
	private final CouponService couponService;
	private final ProductService productService;
	private final OrderAggregateFactory orderAggregateFactory;
	private final UserService userService;
	private final EventPublishingService eventPublishingService;
	private final UserOrderApplicationService userOrderApplicationService;

	public UserOrderApplicationService(DiscountService discountService, CouponService couponService,
	                                   ProductService productService, OrderAggregateFactory orderAggregateFactory,
	                                   UserService userService, EventPublishingService eventPublishingService,
	                                   UserOrderApplicationService userOrderApplicationService) {
		this.discountService = discountService;
		this.couponService = couponService;
		this.productService = productService;
		this.orderAggregateFactory = orderAggregateFactory;
		this.userService = userService;
		this.eventPublishingService = eventPublishingService;
		this.userOrderApplicationService = userOrderApplicationService;
	}

	public Object previewOrder(PreviewOrderCommand cmd) throws Exception {
		OrderAggregate aggregate = userOrderApplicationService.calc(cmd.getUserId(), cmd.getCouponIds(), cmd.getSkuIds(), cmd.getProductItems());
		// 6. 返回
		return SubmitOrderResult.from(aggregate);
	}

	@Transactional
	public SubmitOrderResult submitOrder(SubmitOrderCommand cmd) throws Exception {
		OrderAggregate aggregate = userOrderApplicationService.calc(cmd.getUserId(), cmd.getCouponsIds(), cmd.getSkuIds(), cmd.getProductItems());
		// 6. 异步持久化
		// 7. TODO: 发送“创建订单”事件
		eventPublishingService.publish(null);
		// 8. 返回
		return SubmitOrderResult.from(aggregate);
	}

	public Object previewAfterSale(Object cmd) {
		return null;
	}

	@Transactional
	public Object applyAfterSale(AfterSaleApplyCommand cmd) {
		return null;
	}

	@Transactional
	public Object cancelAfterSale(ApplyCancelCommand cmd) {
		return null;
	}

	@Transactional
	public Object confirmReceipt(ConfirmReceiptCommand cmd) {
		return null;
	}

	private OrderAggregate calc(String userId, Set<String> couponIds, List<String> skuIds, List<PreviewOrderCommand.ProductItem> productItems) throws Exception {
		Buyer buyer = userService.getBuyerById(userId);
		// 1. 验证优惠券 & 商品有效
		couponService.checkCouponValid(couponIds);
		productService.validateProducts(skuIds);
		// 2. 按店铺拆单
		Map<String, List<PreviewOrderCommand.ProductItem>> shopItemMap = productItems.stream()
			.collect(Collectors.groupingBy(PreviewOrderCommand.ProductItem::shopId));
		// 3. 查询折扣
		List<Discount> discounts = discountService.findDiscountByShopId(shopItemMap.keySet());
		// 4. 创建领域对象 & 领域服务计算价格
		OrderAggregate aggregate = orderAggregateFactory.createOrder(
			OrderAggregate.CreateOrderArgs.builder()
				.buyer(buyer)
				.subOrders(List.of())
				.coupons(List.of())
				.discounts(List.of())
				.address(null)
				.build()
		);
		// 5. 计算价格
		aggregate.computePricing();
		return aggregate;
	}

}
