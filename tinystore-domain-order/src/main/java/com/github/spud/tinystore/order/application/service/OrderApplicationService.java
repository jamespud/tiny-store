package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.CancelOrderCommand;
import com.github.spud.tinystore.order.application.command.CreateOrderCommand;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand;
import com.github.spud.tinystore.order.application.result.CreateOrderResult;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult;
import com.github.spud.tinystore.order.domain.model.Coupon;
import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.domain.service.CancelDecisionService;
import com.github.spud.tinystore.order.domain.service.CouponService;
import com.github.spud.tinystore.order.domain.service.OrderDomainService;
import com.github.spud.tinystore.order.domain.service.OrderFactory;
import com.github.spud.tinystore.order.domain.service.OrderOutboxService;
import com.github.spud.tinystore.order.domain.service.OrderPriceCalculationService;
import com.github.spud.tinystore.order.domain.service.ProductService;
import com.github.spud.tinystore.order.domain.service.ProductValidatorService;
import com.github.spud.tinystore.order.domain.service.UserValidatorService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Slf4j
@Service
public class OrderApplicationService {

	private final OrderDomainService orderDomainService;
	private final CancelDecisionService cancelDecisionService;
	@Value("{order.preview.ttl:60}")
	private long previewTTL = 60;

	private final CouponService couponService;
	private final ProductService productService;
	private final OrderPriceCalculationService orderPriceCalculationService;
	private final ProductValidatorService productValidatorService;
	private final UserValidatorService userValidatorService;
	private final OrderFactory orderFactory;
	private final OrderOutboxService orderOutboxService;

	public OrderApplicationService(CouponService couponService,
		OrderPriceCalculationService priceCalculationService,
		ProductValidatorService productValidatorService, UserValidatorService userValidatorService,
		ProductService productService, OrderFactory orderFactory,
		OrderOutboxService orderOutboxService, OrderDomainService orderDomainService,
		CancelDecisionService cancelDecisionService) {
		this.couponService = couponService;
		this.productService = productService;
		this.orderPriceCalculationService = priceCalculationService;
		this.productValidatorService = productValidatorService;
		this.userValidatorService = userValidatorService;
		this.orderFactory = orderFactory;
		this.orderOutboxService = orderOutboxService;
		this.orderDomainService = orderDomainService;
		this.cancelDecisionService = cancelDecisionService;
	}

	/**
	 * 订单预览 验证商品是否下架，计算价格，缓存预览结果
	 *
	 * @param cmd
	 * @return
	 */
	public PreviewOrderResult orderPreview(PreviewOrderCommand cmd) {
		String userId = cmd.getUserId();
		List<String> productIds = cmd.getProductIds();
		// 验证参数
		boolean validPram = validateOrderParam(userId, productIds, cmd.getCoupons(),
			cmd.getAddressId());
		if (!validPram) {
			log.debug("");
			return null;
		}
		// 获取商品和优惠券信息
		List<Product> products = productService.getProductsByIds(productIds);
		List<Coupon> coupons = couponService.getAvailableCoupons(userId);
		// 创建订单聚合
		Order order = orderFactory.createOrder(cmd, products, coupons);
		// 计算价格
		Order discounted = orderPriceCalculationService.calculatePrice(order);
		// 缓存预览结果
		// TODO: 设置过期时间
		return new PreviewOrderResult();
	}

	public CreateOrderResult submitOrder(CreateOrderCommand cmd) {
		String userId = cmd.getUserId();
		List<String> productIds = cmd.getProductIds();
		boolean validPram = validateOrderParam(userId, productIds, cmd.getCoupons(),
			cmd.getAddressId());
		if (!validPram) {
			log.debug("");
			return null;
		}
		List<Product> products = productService.getProductsByIds(productIds);
		List<Coupon> coupons = couponService.getAvailableCoupons(userId);
		// 锁定库存
		boolean productDeducted = productValidatorService.deductProductStocks(cmd.getProducts());
		// 库存不足，抛出异常
		if (!productDeducted) {
			throw new IllegalArgumentException("库存不足");
		}
		boolean couponDeducted = productValidatorService.deductCoupons(cmd.getUserId(),
			cmd.getCoupons());
		// 创建订单聚合
		Order order = orderFactory.createOrder(cmd, products, coupons);
		// 计算价格，防止篡改
		order = orderPriceCalculationService.calculatePrice(order);
		orderDomainService.saveOrderLine(order);
		// 发布订单创建事件
		orderOutboxService.recordEvent(order);
		return new CreateOrderResult();
	}

	public Object cancelPreview(String orderId) {
		// TODO: 检查订单状态，是否可以取消
		boolean canCancel = cancelDecisionService.canCancel(orderId);
		if (!canCancel){
			return false;
		}
		// TODO: 生成幂等键
		return UUID.randomUUID();
	}

	public Object cancelOrder(CancelOrderCommand cmd) {
		// TODO: 根据订单状态取消订单
		// TODO: (opt) 发送取消订单请求给商家, 等待商家确认
		// TODO: (opt) 商家确认后，通过确认接口调用取消订单
		// TODO: 发送取消订单事件
		// TODO: 释放库存, 优惠券等
		// TODO: (支付服务) 异步退款
		return "待确认";
	}

	public boolean validateOrderParam(String userId, List<String> productIds, Set<String> couponIds,
		String addressId) {
		// 检查用户是否可以下单
		boolean canOrder = userValidatorService.canUserPlaceOrder(userId, productIds);
		if (!canOrder) {
			throw new IllegalArgumentException("用户无法下单");
		}
		// 检查商品是否有效
		boolean productsValid = productValidatorService.validateProducts(productIds);
		if (!productsValid) {
			throw new IllegalArgumentException("包含无效商品");
		}
		boolean couponValid = productValidatorService.validateCoupons(couponIds);
		if (!couponValid) {
			throw new IllegalArgumentException("包含无效优惠券");
		}
		// 检查收货地址是否有效
		boolean addressValid = userValidatorService.validateAddress(userId, addressId);
		if (!addressValid) {
			throw new IllegalArgumentException("收货地址无效");
		}
		return true;
	}
}
