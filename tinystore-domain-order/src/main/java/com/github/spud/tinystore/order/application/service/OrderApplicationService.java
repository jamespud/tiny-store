package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.CreateOrderCommand;
import com.github.spud.tinystore.order.application.command.PreviewOrderCommand;
import com.github.spud.tinystore.order.application.result.CreateOrderResult;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult;
import com.github.spud.tinystore.order.domain.service.CouponService;
import com.github.spud.tinystore.order.domain.service.OrderPriceCalculationService;
import com.github.spud.tinystore.order.domain.service.ProductService;
import com.github.spud.tinystore.order.domain.service.ProductValidatorService;
import com.github.spud.tinystore.order.domain.service.UserValidatorService;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Service
public class OrderApplicationService {

	private final CouponService couponService;
	private final ProductService productService;
	private final OrderPriceCalculationService orderPriceCalculationService;
	private final ProductValidatorService productValidatorService;
	private final UserValidatorService userValidatorService;

	public OrderApplicationService(CouponService couponService,
		OrderPriceCalculationService orderPriceCalculationService,
		ProductValidatorService productValidatorService, UserValidatorService userValidatorService,
		ProductService productService) {
		this.couponService = couponService;
		this.productService = productService;
		this.orderPriceCalculationService = orderPriceCalculationService;
		this.productValidatorService = productValidatorService;
		this.userValidatorService = userValidatorService;
	}

	/**
	 * 订单预览 验证商品是否下架，计算价格，使用优惠券达到最优价格，缓存预览结果
	 *
	 * @param cmd
	 * @return
	 */
	@Cacheable(value = "orderPreview", key = "#cmd.skuIds + '-' + #cmd.couponIds")
	public PreviewOrderResult orderPreview(PreviewOrderCommand cmd) {
		String userId = cmd.getUserId();
		List<String> productIds = cmd.getProductIds();
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
		boolean couponValid = productValidatorService.validateCoupons(cmd.getCoupons());
		if (!couponValid) {
			throw new IllegalArgumentException("包含无效优惠券");
		}
		// 检查收货地址是否有效
		boolean addressValid = userValidatorService.validateAddress(userId, cmd.getAddressId());
		if (!addressValid) {
			throw new IllegalArgumentException("收货地址无效");
		}
		// 获取商品和优惠券信息
		List<Object> products = productService.getProductsByIds(productIds);
		List<Object> coupons = couponService.getAvailableCoupons(userId);
		// 计算价格
		Object discounted = orderPriceCalculationService.calculatePrice(products, coupons);

		return new PreviewOrderResult();
	}

	public CreateOrderResult submitOrder(CreateOrderCommand cmd) {
		String userId = cmd.getUserId();
		List<String> productIds = cmd.getProductIds();
		boolean canOrder = userValidatorService.canUserPlaceOrder(userId, productIds);
		if (!canOrder) {
			throw new IllegalArgumentException("用户无法下单");
		}
		// 检查商品是否有效
		boolean productsValid = productValidatorService.validateProducts(productIds);
		if (!productsValid) {
			throw new IllegalArgumentException("包含无效商品");
		}
		boolean couponValid = productValidatorService.validateCoupons(cmd.getCoupons());
		if (!couponValid) {
			throw new IllegalArgumentException("包含无效优惠券");
		}
		// 检查收货地址是否有效
		boolean addressValid = userValidatorService.validateAddress(userId, cmd.getAddressId());
		if (!addressValid) {
			throw new IllegalArgumentException("收货地址无效");
		}
		List<Object> products = productService.getProductsByIds(productIds);
		List<Object> coupons = couponService.getAvailableCoupons(userId);
		// 
		Object total = orderPriceCalculationService.calculatePrice(products, coupons);
		// TODO: 锁定库存
		boolean productDeducted = productValidatorService.deductProductStocks(cmd.getProducts());
		if (!productDeducted) {
			throw new IllegalArgumentException("库存不足");
		}
		// TODO: 优惠券锁定
		boolean couponDeducted = productValidatorService.deductCoupons(cmd.getUserId(),
			cmd.getCoupons());
		if (!couponDeducted) {
			// 回滚库存
			productValidatorService.releaseProductStocks(cmd.getProductMap());
			throw new IllegalArgumentException("优惠券不可用");
		}
		// 计算价格，防止篡改
		total = orderPriceCalculationService.calculatePrice(products, coupons);
		// TODO: 创建订单
		// TODO: 发布订单创建事件
		return new CreateOrderResult();
	}
}
