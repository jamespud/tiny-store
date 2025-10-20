package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.AutoCompleteCommand;
import com.github.spud.tinystore.order.application.command.MoveToAwaitFulfillmentCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.command.PaymentSuccessCommand;
import com.github.spud.tinystore.order.application.command.RefundSucceededCommand;
import com.github.spud.tinystore.order.application.command.RefundSuccessCommand;
import com.github.spud.tinystore.order.application.command.UnpaidTimeoutCancelCommand;
import com.github.spud.tinystore.order.application.command.merchant.AcceptOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.ApproveCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.DeliveredCommand;
import com.github.spud.tinystore.order.application.command.merchant.ExchangeCompletedCommand;
import com.github.spud.tinystore.order.application.command.merchant.MerchantAcceptCommand;
import com.github.spud.tinystore.order.application.command.merchant.RejectCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.ShipOrderCommand;
import com.github.spud.tinystore.order.application.command.user.AfterSaleApplyCommand;
import com.github.spud.tinystore.order.application.command.user.ApplyAfterSaleCommand;
import com.github.spud.tinystore.order.application.command.user.ApplyCancelCommand;
import com.github.spud.tinystore.order.application.command.user.CancelOrderCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmReceiptCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand.MerchantSkuDTO;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand.MerchantSkuDTO.SkuItemDTO;
import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult;
import com.github.spud.tinystore.order.application.result.SubmitOrderResult;
import com.github.spud.tinystore.order.domain.event.DomainEventPublisher;
import com.github.spud.tinystore.order.domain.event.MultiShopOrderCreatedEvent;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.MainOrder;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.domain.model.OrderItem;
import com.github.spud.tinystore.order.domain.model.PricingSummary;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.domain.model.SubOrderItem;
import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
import com.github.spud.tinystore.order.domain.repository.IdempotencyRepository;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.CancelDecisionService;
import com.github.spud.tinystore.order.domain.service.InventoryService;
import com.github.spud.tinystore.order.domain.service.NotifyService;
import com.github.spud.tinystore.order.domain.service.OrderDomainService;
import com.github.spud.tinystore.order.domain.service.OrderNumberService;
import com.github.spud.tinystore.order.domain.service.ProductService;
import com.github.spud.tinystore.order.domain.service.ProductService.SkuBatchQueryResponse;
import com.github.spud.tinystore.order.domain.service.ProductService.SkuDTO;
import com.github.spud.tinystore.order.domain.service.PromotionService;
import com.github.spud.tinystore.order.domain.service.RiskControlService;
import com.github.spud.tinystore.order.domain.service.RiskControlService.OrderRiskCheckRequest;
import com.github.spud.tinystore.order.domain.service.RiskControlService.OrderRiskCheckResponse;
import com.github.spud.tinystore.order.domain.service.RiskControlService.SkuRiskDTO;
import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;
import com.github.spud.tinystore.order.domain.status.OrderStateTransitionService;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyRequest;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient.StockPreOccupyResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CalculateFreightResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.MerchantInfo;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUseCouponResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUseMerchantCouponRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUsePlatformCouponRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse;
import com.github.spud.tinystore.order.interfaces.error.OrderBusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Order Application Service - Enhanced with Rich Aggregate Pattern
 *
 * @author Spud
 * @date 2025/9/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

	private final OrderDomainService orderDomainService;
	private final CancelDecisionService cancelDecisionService;
	private final OrderStateTransitionService orderStateTransitionService;
	private final OrderRepository orderRepository;
	private final IdempotencyRepository idempotencyRepository;
	private final ObjectMapper objectMapper;
	private final PromotionService promotionService;
	private final ProductService productService;
	private final IdempotencyStorage idempotencyStorage;
	private final RiskControlService riskControlService;
	private final InventoryService inventoryService;
	private final DomainEventPublisher domainEventPublisher;
	private final NotifyService notifyService;
	private final OrderNumberService orderNumberService;

	/**
	 * 确认订单
	 * @param command
	 * @return
	 */
	public ConfirmOrderResult confirmOrder(ConfirmOrderCommand command) {
		// TODO: 完成确认订单逻辑
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * 提交订单
	 *
	 * @param cmd
	 * @return
	 * @throws Exception
	 */
	@Cacheable(value = "orderCache", key = "#cmd.idempotentKey", unless = "#result == null")
	@Transactional
	public SubmitOrderResult submitOrder(SubmitOrderCommand cmd) throws Exception {
		String userId = cmd.getUserId();
		// -------------------------- 1. 幂等校验（基于主订单幂等键） --------------------------
		if (idempotencyStorage.exists(cmd.getIdempotentKey())) {
			log.warn("多店铺订单幂等键已存在：idempotencyKey={}", cmd.getIdempotentKey());
			return idempotencyStorage.getResponse(cmd.getIdempotentKey(), CreateOrderResponse.class);
		}

		// -------------------------- 2. 前置校验（风控+SKU合法性+商家一致性） --------------------------
		// 2.1 风控检查（多店铺场景需校验“跨店下单是否异常”）
		OrderRiskCheckResponse riskResponse = riskControlService.checkOrderRisk(
			OrderRiskCheckRequest.builder()
				.userId(cmd.getUserId())
				.addressId(cmd.getAddressId())
				.merchantList(cmd.getMerchantSkus().stream()
					.map(merchantSku -> new RiskControlService.MerchantRiskDto(
						merchantSku.merchantId(),
						merchantSku.skuItems().stream()
							.map(skuItem -> new RiskControlService.SkuRiskDTO(
								skuItem.skuId(),
								skuItem.quantity()
							))
							.toList()
					))
					.toList()
				)
				.build()
		);

		if (!riskResponse.isPass()) {
			log.warn("多店铺订单风控校验未通过：reason={}", riskResponse.getReason());
			throw OrderBusinessException.riskBlocked(riskResponse.getReason());
		}

		// 2.2 批量查询所有SKU信息（含所属商家ID，校验“请求商家ID与SKU实际商家ID一致”）
		Set<String> skuIds = cmd.getSkuIds();
		SkuBatchQueryResponse skuBatchResponse = productService.batchGetSkuInfo(skuIds);
		Map<String, SkuDTO> skuMap = skuBatchResponse.getSkuMap();

		// 2.3 校验SKU合法性+商家一致性（请求的商家ID必须与SKU实际商家ID一致）
		this.validateSkuAndMerchantConsistency(cmd.getMerchantSkus(), skuMap);

		// 2.4 批量查询商家信息（名称、运费政策，用于子订单构建）
		List<String> merchantIds = cmd.getMerchantSkus().stream()
			.map(MerchantSkuDTO::merchantId)
			.toList();
		Map<String, MerchantInfo> merchantMap = promotionService.batchGetMerchantInfo(merchantIds);

		// 生成订单号
		String orderNumber = orderNumberService.generateOrderNumber(cmd.getUserId());

		// -------------------------- 3. 优惠预核销（平台优惠券+商家优惠券） --------------------------
		// 3.1 平台优惠券预核销（跨店可用，仅1张）
		PreUseCouponResponse platformCouponResp = null;
		Money platformDiscountTotal = Money.of(0);
		if (StringUtils.hasText(cmd.getPlatformCouponId())) {
			platformCouponResp = promotionService.preUsePlatformCoupon(
				PreUsePlatformCouponRequest.builder()
					.userId(userId)
					.couponId(cmd.getPlatformCouponId())
					.skus(cmd.getMerchantSkus().stream()
						.flatMap(merchantSkus -> merchantSkus.skuItems().stream())
						.map(item -> new PromotionClient.SkuDetail(
							item.skuId(),
							item.quantity()
						))
						.toList()
					)
					.build()
			);
			if (!platformCouponResp.isValid()) {
				throw new OrderBusinessException(
					"平台优惠券不可用：" + platformCouponResp.getInvalidReason(), "ORDER-4004",
					HttpStatus.BAD_REQUEST
				);
			}
			platformDiscountTotal = Money.of(platformCouponResp.getTotalDiscount());
		}

		// 3.2 商家优惠券预核销
		Map<String, PreUseCouponResponse> merchantCouponMap = new HashMap<>();
		for (MerchantSkuDTO merchantSkus : cmd.getMerchantSkus()) {
			String merchantId = merchantSkus.merchantId();
			String merchantCouponId = merchantSkus.merchantCouponId();
			if (!StringUtils.hasText(merchantCouponId)) {
				continue;
			}

			List<SkuDTO> merchantSkuList = merchantSkus.skuItems().stream()
				.map(item -> new SkuDTO(
					item.skuId(),
					item.quantity(),
					skuMap.get(item.skuId()).getUnitPrice()
				))
				.toList();

			PreUseCouponResponse merchantCouponResp = promotionService.preUseMerchantCoupon(
				PreUseMerchantCouponRequest.builder()
					.userId(userId)
					.merchantId(merchantSkus.merchantId())
					.couponId(merchantCouponId)
					.skus(merchantSkus.skuItems().stream()
						.map(item -> new PromotionClient.SkuDetail(
								item.skuId(),
								item.quantity()
							)
						).toList()
					)
					.build()
			);
			if (!merchantCouponResp.isValid()) {
				// 商家优惠券核销失败，回滚已核销的平台优惠券
				if (platformCouponResp != null) {
					promotionService.rollbackCouponUse(platformCouponResp.getLockId());
				}
				throw new OrderBusinessException(
					"商家[" + merchantId + "]优惠券不可用：" + merchantCouponResp.getInvalidReason(),
					"ORDER-4005",
					org.springframework.http.HttpStatus.BAD_REQUEST
				);
			}
			merchantCouponMap.put(merchantId, merchantCouponResp);
		}

		// -------------------------- 4. 库存预占（批量预占所有商家的SKU，按商家分组记录预占ID） --------------------------
		// 4.1 构建所有SKU的库存预占请求
		List<StockPreOccupyRequest> allPreOccupyList = cmd.getMerchantSkus().stream()
			.flatMap(skus -> skus.skuItems().stream())
			.map(item -> new StockPreOccupyRequest(
				item.skuId(),
				item.quantity()
			))
			.toList();

		// 4.2 批量预占库存
		StockPreOccupyResponse stockResp = inventoryService.preOccupyStock(allPreOccupyList);

		if (!stockResp.isSuccess()) {
			// 库存不足，回滚所有已核销的优惠券
			this.rollbackAllCoupons(platformCouponResp, merchantCouponMap);
			throw OrderBusinessException.stockInsufficient(stockResp.getLackSkuId());
		}

		// 按商家分组存储库存预占ID（后续关联子订单）
		Map<String, String> merchantStockPreOccupyMap = this.groupStockPreOccupyByMerchant(
			cmd.getMerchantSkus(), skuMap, stockResp.getPreOccupyIds()
		);

		try {
			// -------------------------- 5. 按商家拆分并构建子订单 --------------------------
			List<SubOrder> subOrderList = new ArrayList<>();
			for (MerchantSkuDTO merchantSkus : cmd.getMerchantSkus()) {
				String merchantId = merchantSkus.merchantId();
				MerchantInfo merchantInfo = merchantMap.get(merchantId);
				PreUseCouponResponse merchantCouponResp = merchantCouponMap.get(merchantId);

				// 5.1 构建子订单SKU明细
				List<SubOrderItem> subItemList = merchantSkus.skuItems().stream()
					.map(item -> {
						SkuDTO skuDTO = skuMap.get(item.skuId());
						return SubOrderItem.builder()
							// TODO: 填充参数
							.build();
					})
					.toList();

				// 5.2 计算子订单基础金额（商品总价、商家优惠、运费）
				Money subGoodsTotal = subItemList.stream()
					.map(SubOrderItem::getItemTotalPrice)
					.reduce(Money.of(0), Money::add);
				Money subMerchantDiscount = merchantCouponResp != null ?
					Money.of(merchantCouponResp.getTotalDiscount()) : Money.of(0);

				// 计算子订单运费（调用物流服务，传入商家地址、SKU重量）
				Money subFreight = this.calculateMerchantFreight(merchantSkus, subItemList, skuMap,
					merchantInfo);

				// 5.3 暂存子订单基础信息（平台优惠分摊后续统一处理）
				subOrderList.add(
					SubOrder.builder()
						.mainOrderNo(OrderNo.of(orderNumber))
						// TODO: 填充参数
						.build()
				);
			}

			// -------------------------- 6. 平台优惠分摊（按子订单商品总价占比拆分） --------------------------
			List<SubOrder> subOrdersWithDiscount = promotionService.allocatePlatformDiscount(subOrderList,
				platformDiscountTotal);

			// -------------------------- 7. 构建主订单 --------------------------

			// 7.1 生成主订单号
			// TODO: 可替换为更优雅的订单号生成策略（如雪花算法、Redis等）
			String mainOrderNo =
				"DO_M_" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);

			// 7.2 关联主订单号到所有子订单
//			subOrdersWithDiscount.forEach(sub -> sub.setMainOrderNo(mainOrderNo));

			// 7.3 计算主订单总实付金额（所有子订单实付金额之和）
			Money totalPayAmount = subOrdersWithDiscount.stream()
				.map(SubOrder::getPricingSummary)
				.map(PricingSummary::payable)
				.reduce(Money.of(0), Money::add);

			// 7.4 构建主订单实体
			MainOrder mainOrder = MainOrder.builder()
				.subOrders(subOrdersWithDiscount)
				// TODO: 填充参数
				.build();

			// -------------------------- 8. 数据落库（主订单+子订单+明细） --------------------------
			orderRepository.save(mainOrder);
			log.info("多店铺订单数据落库成功：mainOrderNo={}, 子订单数={}", mainOrderNo,
				subOrdersWithDiscount.size());

			// -------------------------- 9. 保存订单创建事件（支付、通知等） --------------------------
			domainEventPublisher.publish(
				new MultiShopOrderCreatedEvent(
					mainOrderNo,
					userId,
					subOrdersWithDiscount.stream()
						.map(sub -> new MultiShopOrderCreatedEvent.SubOrderRef(
							sub.getOrderNo().value(),
							sub.getMerchantId(),
							sub.getStockPreOccupyIds(),
							sub.getCouponLockId()
						))
						.collect(Collectors.toList())
				)
			);

			SubmitOrderResult result = this.buildSubmitOrderResult(mainOrder);

			// -------------------------- 10. 发送用户通知（合并下单成功） --------------------------
			notifyService.sendOrderCreateNotice(
				userId,
				mainOrderNo,
				totalPayAmount.amount(),
				subOrdersWithDiscount.size()
			);

			log.info("订单创建完成：mainOrderNo={}, totalPayAmount={}, 子订单数={}",
				mainOrderNo, totalPayAmount.amount(), subOrdersWithDiscount.size());
			return result;
		} catch (Exception e) {
			// 异常回滚：释放库存+释放所有优惠券
			log.error("创建多店铺订单异常，触发全局回滚：userId={}, error={}", userId, e.getMessage(), e);
			// 回滚库存预占
			inventoryService.rollbackPreOccupy(stockResp.getPreOccupyIds());
			// 回滚所有优惠券
			this.rollbackAllCoupons(platformCouponResp, merchantCouponMap);
			throw e;
		}
	}

	/**
	 * Handle payment success callback (with idempotency)
	 */
	@Transactional
	public void handlePaymentSucceeded(PaymentSucceededCommand cmd) {
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forPaymentCallback(cmd.getPaymentId(), cmd.getOrderId(), cmd.getAmount().toString());

		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Payment callback already processed: {}", idempotencyKey);
			return; // Already processed
		}

		try {
			log.info("Processing payment success for order: {}", cmd.getOrderId());

			OrderItem order = orderRepository.findById(cmd.getOrderId())
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

			order.onPaymentSuccess(OrderAggregate.PaymentSuccessArgs.builder()
				.paymentId(cmd.getPaymentId())
				.amount(cmd.getAmount())
				.isDeposit(cmd.isDeposit())
				.isFinalPayment(cmd.isFinalPayment())
				.build());

			List<OrderDomainEvent> events = order.pullDomainEvents();
			List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
			orderRepository.saveWithOutbox(order, envelopes);

			log.info("Payment success processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			throw e;
		}
	}

	/**
	 * Merchant receives order
	 */
	@Transactional
	public void merchantAcceptOrder(AcceptOrderCommand cmd) {
		log.info("Merchant receiving order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.onMerchantAccept();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Merchant receive processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Ship order
	 */
	@Transactional
	public void ship(ShipOrderCommand cmd) {
		log.info("Shipping order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		// TODO: 
		order.onShip(OrderAggregate.ShipArgs.builder()
			.subOrderId(cmd.getOrderId())
			.shipmentInfo(cmd.getLogistics().getTrackingNo())
			.build());

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Ship processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Handle delivery callback (with idempotency)
	 */
	@Transactional
	public void handleDelivered(DeliveredCommand cmd) {
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forLogisticsCallback(cmd.getOrderId(), "DELIVERED", cmd.getIdempotencyKey());

		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Delivery callback already processed: {}", idempotencyKey);
			return;
		}

		try {
			log.info("Processing delivery for order: {}", cmd.getOrderId());

			OrderItem order = orderRepository.findById(cmd.getOrderId())
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

			// TODO: 
			order.onDelivered(OrderAggregate.DeliveredArgs.builder()
				.shipmentInfo(cmd.getTrackingNo())
				.afterSaleWindowOpen(false)
				.build());

			List<OrderDomainEvent> events = order.pullDomainEvents();
			List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
			orderRepository.saveWithOutbox(order, envelopes);

			log.info("Delivery processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			throw e;
		}
	}

	/**
	 * Auto complete order
	 */
	@Transactional
	public void autoComplete(AutoCompleteCommand cmd) {
		log.info("Auto completing order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.onAutoComplete();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Auto complete processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Apply for after sale
	 */
	@Transactional
	public void applyAfterSale(ApplyAfterSaleCommand cmd) {
		log.info("Applying after sale for order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.requestAfterSale();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("After sale application processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Handle refund success callback (with idempotency)
	 */
	@Transactional
	public void handleRefundSucceeded(RefundSucceededCommand cmd) {
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forRefundCallback(cmd.getRefundId(), cmd.getOrderId());

		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Refund callback already processed: {}", idempotencyKey);
			return;
		}

		try {
			log.info("Processing refund success for order: {}", cmd.getOrderId());

			OrderItem order = orderRepository.findById(cmd.getOrderId())
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

			order.onRefundSuccess();

			List<OrderDomainEvent> events = order.pullDomainEvents();
			List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
			orderRepository.saveWithOutbox(order, envelopes);

			log.info("Refund success processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			throw e;
		}
	}

	/**
	 * Apply for cancellation
	 */
	@Transactional
	public void applyCancel(ApplyCancelCommand cmd) {
		log.info("Applying cancel for order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.requestCancel();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Cancel application processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Approve cancellation
	 */
	@Transactional
	public void approveCancel(ApproveCancelOrderCommand cmd) {
		log.info("Approving cancel for order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.approveCancel();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Cancel approval processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Reject cancellation
	 */
	@Transactional
	public void rejectCancel(RejectCancelOrderCommand cmd) {
		log.info("Rejecting cancel for order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.rejectCancel();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Cancel rejection processed for order: {}", cmd.getOrderId());
	}

	/**
	 * Handle exchange completion
	 */
	@Transactional
	public void exchangeCompleted(ExchangeCompletedCommand cmd) {
		log.info("Processing exchange completion for order: {}", cmd.getOrderId());

		OrderItem order = orderRepository.findById(cmd.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

		order.onExchangeCompleted();

		List<OrderDomainEvent> events = order.pullDomainEvents();
		List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
		orderRepository.saveWithOutbox(order, envelopes);

		log.info("Exchange completion processed for order: {}", cmd.getOrderId());
	}

	private List<OutboxEventEnvelope> mapToOutboxEnvelopes(List<OrderDomainEvent> events) {
		return events.stream()
			.map(event -> {
				String topic = determineTopicForEvent(event);
				String payload = serializeEvent(event);
				return OutboxEventEnvelope.fromDomainEvent(event, topic, payload);
			})
			.toList();
	}

	private String determineTopicForEvent(OrderDomainEvent event) {
		return switch (OrderEventType.valueOf(event.getType().toString())) {
			case ORDER_CREATED -> "order.created";
			case ORDER_PAID -> "order.payment.succeeded";
			case ORDER_SHIPPED -> "order.shipped";
			case ORDER_COMPLETED -> "order.completed";
			case ORDER_CANCELLED -> "order.cancelled";
			case AFTERSALE_REQUESTED -> "order.refund.requested";
			case AFTERSALE_COMPLETED -> "order.refund.completed";
			default -> "order.general";
		};
	}

	private String serializeEvent(OrderDomainEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize event", e);
		}
	}

	// ========== LEGACY METHODS (PRESERVED) ==========

	/**
	 * 订单预览 验证商品是否下架，计算价格，缓存预览结果
	 *
	 * @param cmd
	 * @return
	 */
	public ConfirmOrderResult orderPreview(ConfirmOrderCommand cmd) {
		return null;
	}

	public Object cancelPreview(String orderId) {
		// TODO: 检查订单状态，是否可以取消
		boolean canCancel = cancelDecisionService.canCancel(orderId);
		if (!canCancel) {
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

	// ==================== 新增的订单主流程接口方法 ====================

	/**
	 * 支付成功回调处理
	 */
	public void onPaymentSuccess(PaymentSuccessCommand cmd) {
		// 1. 幂等性检查（由网关处理，这里仅记录日志）
		log.info("Processing payment success for order: {}, payType: {}, eventId: {}",
			cmd.getOrderId(), cmd.getPayType(), cmd.getEventId());

		// 2. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		// 3. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
		// CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
		// 临时使用占位符
		CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;

		// 4. 应用状态机过渡
		boolean isDeposit = false; // 根据 payType 判断，暂时默认为全款支付
		boolean isFinalPayment = true; // 根据订单类型判断，暂时默认为最终支付
		CoreFlowStatus newStatus = orderStateTransitionService.paymentSuccess(currentStatus, isDeposit,
			isFinalPayment);

		// 5. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
		// order.setStatus(orderStatusTranslator.toLegacy(newStatus));
		orderDomainService.save(order, expectedVersion);

		// 6. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Payment success", "system", cmd.getEventId());

		// 7. 记录 Outbox 事件
		orderDomainService.recordOutbox(order, "payment.success", cmd);

		log.info("Payment success processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 商家接单
	 */
	public void merchantAccept(MerchantAcceptCommand cmd) {
		log.info("Merchant accepting order: {}, operator: {}", cmd.getOrderId(), cmd.getOperatorId());

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		// 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
		// CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
		// 临时使用占位符
		CoreFlowStatus currentStatus = CoreFlowStatus.PAID_CONFIRMED;

		// 3. 应用状态机过渡
		CoreFlowStatus newStatus = orderStateTransitionService.moveToAwaitingFulfillment(currentStatus);

		// 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
		// order.setStatus(orderStatusTranslator.toLegacy(newStatus));
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Merchant accepted", cmd.getOperatorId(), cmd.getRequestId());

		// 6. 记录 Outbox 事件
		orderDomainService.recordOutbox(order, "merchant.accept", cmd);

		log.info("Merchant accept processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 商家发货
	 */
	public void shipOrder(ShipOrderCommand cmd) {
		log.info("Shipping order: {}, operator: {}, logistics: {}",
			cmd.getOrderId(), cmd.getOperatorId(), cmd.getLogistics().getCompanyName());

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		// 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
		// CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
		// 临时使用占位符
		CoreFlowStatus currentStatus = CoreFlowStatus.AWAITING_FULFILLMENT;

		// 3. 应用状态机过渡
		CoreFlowStatus newStatus = orderStateTransitionService.startFulfillment(currentStatus);

		// 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
		// order.setStatus(orderStatusTranslator.toLegacy(newStatus));
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Order shipped", cmd.getOperatorId(), cmd.getRequestId());

		// 6. 记录 Outbox 事件
		orderDomainService.recordOutbox(order, "order.shipped", cmd);

		log.info("Ship order processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 商家同意取消
	 */
	public void approveCancelRequest(ApproveCancelOrderCommand cmd) {
		// TODO: 实现取消审批通过逻辑
		// TODO: 调用状态机 cancelApproved 方法
		// TODO: 触发退款流程
		log.info("Approving cancel request for order: {}, operator: {}", cmd.getOrderId(),
			cmd.getOperatorId());
	}

	/**
	 * 商家拒绝取消
	 */
	public void rejectCancelRequest(RejectCancelOrderCommand cmd) {
		// TODO: 实现取消审批拒绝逻辑
		// TODO: 调用状态机 cancelRejected 方法
		// TODO: 回退到之前状态
		log.info("Rejecting cancel request for order: {}, reason: {}", cmd.getOrderId(),
			cmd.getReasonCode());
	}

	/**
	 * 物流妥投处理
	 */
	public void onLogisticsDelivered(DeliveredCommand cmd) {
		log.info("Processing delivery for order: {}, source: {}", cmd.getOrderId(), cmd.getSource());

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		// 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
		// CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
		// 临时使用占位符
		CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;

		// 3. 应用状态机过渡 (启用售后观察期)
		boolean afterSaleWindowOpen = true; // 物流妥投后开启售后观察期
		CoreFlowStatus newStatus = orderStateTransitionService.delivered(currentStatus,
			afterSaleWindowOpen);

		// 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
		// order.setStatus(orderStatusTranslator.toLegacy(newStatus));
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Logistics delivered", "system", cmd.getEventId());

		// 6. 记录 Outbox 事件
		orderDomainService.recordOutbox(order, "logistics.delivered", cmd);

		log.info("Logistics delivery processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 用户确认收货
	 */
	public void confirmReceipt(ConfirmReceiptCommand cmd) {
		log.info("User confirming receipt for order: {}, user: {}", cmd.getOrderId(), cmd.getUserId());

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		// 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
		// CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
		// 临时使用占位符
		CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;

		// 3. 应用状态机过渡 (用户确认收货，不开启售后观察期)
		boolean afterSaleWindowOpen = false; // 用户主动确认，直接完成
		CoreFlowStatus deliveredStatus = orderStateTransitionService.delivered(currentStatus,
			afterSaleWindowOpen);
		CoreFlowStatus newStatus = orderStateTransitionService.completeIfNoAfterSale(deliveredStatus);

		// 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
		// order.setStatus(orderStatusTranslator.toLegacy(newStatus));
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"User confirmed receipt", cmd.getUserId(), cmd.getRequestId());

		// 6. 记录 Outbox 事件
		orderDomainService.recordOutbox(order, "user.confirm_receipt", cmd);

		log.info("User receipt confirmation processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 申请售后
	 */
	public String applyAfterSale(AfterSaleApplyCommand cmd) {
		// TODO: 实现售后申请逻辑
		// TODO: 调用状态机 requestAfterSale 方法
		// TODO: 创建售后单
		log.info("Applying after-sale for order: {}, type: {}", cmd.getOrderId(), cmd.getType());
		return UUID.randomUUID().toString(); // 返回售后单ID
	}

	/**
	 * 退款成功回调处理
	 */
	public void onRefundSuccess(RefundSuccessCommand cmd) {
		// TODO: 实现退款成功处理逻辑
		// TODO: 调用状态机 refundSuccess 方法
		// TODO: 处理优惠券回滚
		log.info("Processing refund success for order: {}, amount: {}", cmd.getOrderId(),
			cmd.getAmount());
	}

	/**
	 * 自动完成订单
	 */
//    public void autoComplete(AutoCompleteCommand cmd) {
//        // TODO: 实现自动完成逻辑
//        // TODO: 调用状态机 completeIfNoAfterSale 方法
//        // TODO: 检查是否满足自动完成条件
//        log.info("Auto completing order: {}, grace days: {}", cmd.getOrderId(), cmd.getGraceDays());
//    }

	/**
	 * 支付超时自动取消
	 */
	public void timeoutCancel(UnpaidTimeoutCancelCommand cmd) {
		// TODO: 实现超时取消逻辑
		// TODO: 调用状态机 cancelRequest -> cancelApproved 方法
		// TODO: 释放库存和优惠券
		log.info("Timeout cancelling order: {}, schedule: {}", cmd.getOrderId(), cmd.getScheduleId());
	}

	/**
	 * 转待履约（保障性作业）
	 */
	public void moveToAwaitFulfillment(MoveToAwaitFulfillmentCommand cmd) {
		// TODO: 实现转待履约逻辑
		// TODO: 调用状态机 moveToAwaitingFulfillment 方法
		log.info("Moving order to await fulfillment: {}", cmd.getOrderId());
	}

	public boolean validateOrderParam(String userId, List<String> productIds, Set<String> couponIds,
		String addressId) {

		return true;
	}

	// -------------------------- 工具方法 --------------------------
	private void validateSkuAndMerchantConsistency(
		List<MerchantSkuDTO> merchantSkus,
		Map<String, SkuDTO> skuMap
	) throws Exception {
		for (var merchantSku : merchantSkus) {
			String requestMerchantId = merchantSku.merchantId();
			for (SkuItemDTO item : merchantSku.skuItems()) {
				String skuId = item.skuId();
				SkuDTO skuDTO = skuMap.get(skuId);

				// 1. 校验SKU存在且已上架
				if (!skuDTO.isAvailable()) {
					throw OrderBusinessException.skuUnavailable(skuId);
				}

				// 2. 校验请求商家ID与SKU实际商家ID一致
				String actualMerchantId = skuDTO.getMerchantId();
				if (!requestMerchantId.equals(actualMerchantId)) {
					throw new OrderBusinessException(
						"SKU[" + skuId + "]所属商家[" + actualMerchantId + "]与请求商家[" + requestMerchantId
							+ "]不一致",
						"ORDER-4006",
						HttpStatus.BAD_REQUEST
					);
				}
			}
		}
	}

	// -------------------------- 工具方法：计算商家运费（按商家运费政策） --------------------------
	private Money calculateMerchantFreight(
		ConfirmOrderCommand.MerchantSkuDTO merchantGroup,
		List<SubOrderItem> subItemList,
		Map<String, SkuDTO> skuMap,
		MerchantInfo merchantInfo
	) {
		// 1. 计算该商家下所有SKU的总重量（用于运费计算）
		double totalWeight = subItemList.stream()
			.map(item -> skuMap.get(item.getSkuId()).getWeight() * item.getQuantity())
			.reduce(0.0, Double::sum);

		// 2. 调用营销服务计算运费（传入商家地址、总重量、商家运费政策）
//		CalculateFreightResponse freightResponse = promotionService.calculateMerchantFreight(
//			CalculateMerchantFreightRequest.builder()
//				.merchantId(merchantGroup.merchantId())
//				.totalWeight(totalWeight)
//				.freeFreightThreshold(merchantDTO.getFreeFreightThreshold()) // 商家满额包邮阈值
//				.baseFreight(merchantDTO.getBaseFreight()) // 商家基础运费
//				.build()
//		);
		CalculateFreightResponse freightResponse = null;
		return Money.of(freightResponse.getFreightAmount());
	}

	// -------------------------- 工具方法：回滚所有优惠券（平台+商家） --------------------------
	private void rollbackAllCoupons(
		PreUseCouponResponse platformCouponResp,
		Map<String, PreUseCouponResponse> merchantCouponMap
	) {
		// 回滚平台优惠券
		if (platformCouponResp != null) {
			promotionService.rollbackCouponUse(platformCouponResp.getLockId());
			log.info("回滚平台优惠券：couponId={}, lockId={}", platformCouponResp.getCouponId(),
				platformCouponResp.getLockId());
		}
		// 回滚商家优惠券
		for (Map.Entry<String, PreUseCouponResponse> entry : merchantCouponMap.entrySet()) {
			String merchantId = entry.getKey();
			PreUseCouponResponse resp = entry.getValue();
			promotionService.rollbackCouponUse(resp.getLockId());
			log.info("回滚商家[{}]优惠券：couponId={}, lockId={}", merchantId, resp.getCouponId(),
				resp.getLockId());
		}
	}

	// -------------------------- 工具方法：按商家分组库存预占ID --------------------------
	private Map<String, String> groupStockPreOccupyByMerchant(
		List<MerchantSkuDTO> merchantGroups,
		Map<String, SkuDTO> skuMap,
		List<String> preOccupyIds
	) {
		// 先构建“SKU ID → 预占ID”映射（假设库存服务返回顺序与请求顺序一致）
		List<String> skuIdsInRequestOrder = this.flattenSkuList(merchantGroups).stream()
			.map(SkuRiskDTO::getSkuId)
			.toList();
		Map<String, String> skuPreOccupyMap = new HashMap<>();
		for (int i = 0; i < skuIdsInRequestOrder.size(); i++) {
			skuPreOccupyMap.put(skuIdsInRequestOrder.get(i), preOccupyIds.get(i));
		}

		// 再按商家分组预占ID（逗号分隔）
		Map<String, String> merchantPreOccupyMap = new HashMap<>();
		for (MerchantSkuDTO group : merchantGroups) {
			String merchantId = group.merchantId();
			List<String> merchantPreOccupyIds = group.skuItems().stream()
				.map(item -> skuPreOccupyMap.get(item.skuId()))
				.collect(Collectors.toList());
			merchantPreOccupyMap.put(merchantId, String.join(",", merchantPreOccupyIds));
		}
		return merchantPreOccupyMap;
	}

	// -------------------------- 工具方法：扁平化SKU列表（用于风控/库存） --------------------------
	private List<SkuRiskDTO> flattenSkuList(
		List<MerchantSkuDTO> merchantGroups
	) {
		return merchantGroups.stream()
			.flatMap(group -> group.skuItems().stream()
				.map(item -> new SkuRiskDTO(
					item.skuId(),
					item.quantity()
				))
			)
			.collect(Collectors.toList());
	}

	// -------------------------- 工具方法：构建多店铺订单 Result -------------------------
	private SubmitOrderResult buildSubmitOrderResult(MainOrder mainOrder) {
		SubmitOrderResult result = new SubmitOrderResult();
		// TODO: 填充 Result 字段
		return result;
	}


	// -------------------------- 工具方法：构建多店铺订单响应DTO --------------------------
	private CreateOrderResponse buildMultiShopOrderResponse(
		MainOrder mainOrder) {
		List<SubOrder> subOrders = mainOrder.getSubOrders();
		CreateOrderResponse response = new CreateOrderResponse();
//		response.setMainOrderNo(mainOrder.getOrderNo().value());
//		response.setMainOrderStatus(mainOrder.getMainStatus());
//		response.setTotalPayAmount(mainOrder.getTotalPayAmount());
////		response.setMergePayUrl(mergePayResponse.getMergePayUrl());
//		response.setPlatformDiscount(mainOrder.getPlatformDiscount());
//
//		// 构建子订单响应列表
//		List<CreateOrderResponse.SubOrderResponseDTO> subRespList = subOrders.stream()
//			.map(sub -> {
//				CreateOrderResponse.SubOrderResponseDTO subResp = new CreateOrderResponse.SubOrderResponseDTO();
//				subResp.setSubOrderNo(sub.getOrderNo().value());
//				subResp.setMerchantId(sub.getMerchantId());
//				subResp.setMerchantName(sub.getMerchantName());
//				subResp.setSubPayAmount(sub.getSubPayAmount());
////				subResp.setSubOrderStatus(sub.getStatus());
//				subResp.setMerchantDiscount(sub.getSubMerchantDiscount());
//				subResp.setSubPlatformDiscount(sub.getSubPlatformDiscount());
//				subResp.setSubFreight(sub.getSubFreight());
//
//				// 构建子订单SKU明细响应
//				List<CreateOrderResponse.SubOrderItemResponseDTO> itemRespList = sub.getSubItems()
//					.stream()
//					.map(item -> {
//						CreateOrderResponse.SubOrderItemResponseDTO itemResp = new CreateOrderResponse.SubOrderItemResponseDTO();
//						itemResp.setSkuId(item.getSkuId());
//						itemResp.setSkuName(item.getSkuName());
//						itemResp.setSkuImage(item.getSkuImage());
//						itemResp.setSpecCombination(item.getSpecCombination());
//						itemResp.setUnitPrice(item.getUnitPrice());
//						itemResp.setQuantity(item.getQuantity());
//						itemResp.setItemTotalPrice(item.getItemTotalPrice());
//						itemResp.setItemPlatformDiscount(item.getItemPlatformDiscount());
//						return itemResp;
//					})
//					.collect(Collectors.toList());
//				subResp.setSubItemList(itemRespList);
//				return subResp;
//			})
//			.collect(Collectors.toList());
//		response.setSubOrderList(subRespList);
		return response;
	}
}
