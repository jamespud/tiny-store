package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.AutoCompleteCommand;
import com.github.spud.tinystore.order.application.command.MoveToAwaitFulfillmentCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.command.RefundSucceededCommand;
import com.github.spud.tinystore.order.application.command.UnpaidTimeoutCancelCommand;
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
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand.MerchantSkuDTO;
import com.github.spud.tinystore.order.application.command.user.ConfirmReceiptCommand;
import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.application.result.ConfirmOrderResult;
import com.github.spud.tinystore.order.application.result.SubmitOrderResult;
import com.github.spud.tinystore.order.domain.event.DomainEventPublisher;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.MainOrder;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.OrderItem;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.domain.model.SubOrderItem;
import com.github.spud.tinystore.order.domain.repository.IdempotencyRepository;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.CancelDecisionService;
import com.github.spud.tinystore.order.domain.service.InventoryService;
import com.github.spud.tinystore.order.domain.service.NotifyService;
import com.github.spud.tinystore.order.domain.service.OrderDomainService;
import com.github.spud.tinystore.order.domain.service.OrderNumberService;
import com.github.spud.tinystore.order.domain.service.ProductService;
import com.github.spud.tinystore.order.domain.service.ProductService.SkuDTO;
import com.github.spud.tinystore.order.domain.service.PromotionService;
import com.github.spud.tinystore.order.domain.service.RiskControlService;
import com.github.spud.tinystore.order.domain.service.RiskControlService.SkuRiskDTO;
import com.github.spud.tinystore.order.domain.statemachine.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.OrderStatus;
import com.github.spud.tinystore.order.domain.statemachine.status.PaymentStatus;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutCommitResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutQuoteResponse;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.CheckoutReleaseRequest;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.MerchantInfo;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PreUseCouponResponse;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse;
import com.github.spud.tinystore.order.infrastructure.audit.AuditService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.interfaces.error.OrderBusinessException;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.audit.AuditEntry;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.domain.event.OrderEventTypeConstants;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderItemEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderMainEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OrderSubEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderItemJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderMainJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderSubJpaRepository;
import com.github.spud.tinystore.order.infrastructure.tenant.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Order Application Service - Enhanced with Rich Aggregate Pattern
 *
 * @author Spud
 * @date 2025/9/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class OrderApplicationService {

	private final OrderDomainService orderDomainService;
	private final CancelDecisionService cancelDecisionService;
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
	private final AuditService auditService;
	private final ObjectProvider<AuditRecorder> auditRecorderProvider;
	private final OrderMetrics orderMetrics;
    private final OutboxEventService outboxEventService;
	private final OrderMainJpaRepository orderMainJpaRepository;
	private final OrderSubJpaRepository orderSubJpaRepository;
	private final OrderItemJpaRepository orderItemJpaRepository;

	/**
	 * 确认订单
	 * @param command
	 * @return
	 */
	public ConfirmOrderResult confirmOrder(ConfirmOrderCommand command) {
		// Minimal preview implementation: gather SKU prices and assemble a lightweight preview
		var skuResp = productService.batchGetSkuInfo(command.getSkuIds());
		java.util.Map<String, com.github.spud.tinystore.order.domain.service.ProductService.SkuDTO> skuMap =
			skuResp != null ? skuResp.getSkuMap() : java.util.Collections.emptyMap();

		java.util.List<ConfirmOrderResult.ShopProductSnapshot> shopLines = new java.util.ArrayList<>();
		com.github.spud.tinystore.order.domain.model.Money total = com.github.spud.tinystore.order.domain.model.Money.of(0);

		for (var merchantSkus : command.getMerchantSkus()) {
			java.util.List<ConfirmOrderResult.ProductSnapshot> products = new java.util.ArrayList<>();
			com.github.spud.tinystore.order.domain.model.Money shopTotal = com.github.spud.tinystore.order.domain.model.Money.of(0);
			for (var item : merchantSkus.skuItems()) {
				var sku = skuMap.get(item.skuId());
				long unit = sku != null ? sku.getUnitPrice() : 0L;
				int qty = item.quantity() != null ? item.quantity() : 0;
				com.github.spud.tinystore.order.domain.model.Money unitMoney = com.github.spud.tinystore.order.domain.model.Money.of(unit);
				com.github.spud.tinystore.order.domain.model.Money payable = unitMoney.multiply(qty);
				products.add(new ConfirmOrderResult.ProductSnapshot(item.skuId(), unitMoney, payable, qty));
				shopTotal = shopTotal.add(payable);
			}
			shopLines.add(new ConfirmOrderResult.ShopProductSnapshot(merchantSkus.merchantId(), products, shopTotal));
			total = total.add(shopTotal);
		}

		ConfirmOrderResult.OrderSummary summary = new ConfirmOrderResult.OrderSummary(
			total,
			total,
			java.util.List.of(),
			java.util.List.of(),
			java.util.List.of()
		);

		long expireSeconds = java.time.Duration.ofMinutes(15).getSeconds();
		return new ConfirmOrderResult(shopLines, summary, expireSeconds);
	}

	/**
	 * 提交订单
	 */
	@Transactional(rollbackFor = Exception.class)
	public SubmitOrderResult submitOrder(SubmitOrderCommand cmd) throws Exception {
		log.info("Submitting order for user: {} idempotency={}", cmd.getUserId(), cmd.getIdempotentKey());

		// Idempotency: if key provided and exists, return cached result
		if (cmd.getIdempotentKey() != null && !cmd.getIdempotentKey().isBlank()) {
			try {
				if (idempotencyStorage.exists(cmd.getIdempotentKey())) {
					var cached = idempotencyStorage.getResponse(
						cmd.getIdempotentKey(),
						com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse.class
					);
					if (cached != null && cached.getMainOrderNo() != null) {
						SubmitOrderResult r = new SubmitOrderResult();
						r.setMainOrderNo(com.github.spud.tinystore.order.domain.model.vo.OrderNo.of(cached.getMainOrderNo()));
						// lines/summary 可缺省不回放；主要保证订单号一致
						log.info("Idempotent replay hit for submit, mainOrderNo={}", cached.getMainOrderNo());
						return r;
					}
				}
			} catch (Exception e) {
				log.warn("Idempotency storage check failed, proceeding: {}", e.getMessage());
			}
		}

		// Minimal checks and build preview-like order then persist a lightweight MainOrder
		var skuResp = productService.batchGetSkuInfo(cmd.getSkuIds());
		java.util.Map<String, com.github.spud.tinystore.order.domain.service.ProductService.SkuDTO> skuMap =
			skuResp != null ? skuResp.getSkuMap() : java.util.Collections.emptyMap();

		String tenantId = TenantContext.getTenantId();
		if (tenantId == null || tenantId.isBlank()) {
			tenantId = "default";
		}

		String mainOrderNo = orderNumberService.generateOrderNumber(cmd.getUserId());
		com.github.spud.tinystore.order.domain.model.vo.OrderNo ono = com.github.spud.tinystore.order.domain.model.vo.OrderNo.of(mainOrderNo);

		CheckoutQuoteResponse quoteResp = promotionService.checkoutQuote(
			buildPromotionIdempotencyKey(cmd.getIdempotentKey(), "quote", mainOrderNo),
			buildCheckoutQuoteRequest(cmd, skuMap)
		);
		if (quoteResp == null || quoteResp.getSnapshot() == null || quoteResp.getStatus() == null
			|| !quoteResp.getStatus().name().startsWith("OK")) {
			throw new IllegalStateException("promotion quote failed");
		}
		var snapshot = quoteResp.getSnapshot();
		long totalAmountCents = snapshot.getItemsTotalCents() + snapshot.getShippingFeeCents();
		long discountAmountCents = snapshot.getPromotionDiscountTotalCents() + snapshot.getCouponDiscountTotalCents();
		long payableAmountCents = snapshot.getPayableCents();
		Money total = Money.of(totalAmountCents);
		Money payable = Money.of(payableAmountCents);

		java.util.List<com.github.spud.tinystore.order.application.result.ConfirmOrderResult.ShopProductSnapshot> shopLines =
			buildShopLines(cmd.getMerchantSkus(), snapshot, total.currency());

		OrderMainEntity mainEntity = new OrderMainEntity()
			.setOrderNo(mainOrderNo)
			.setTenantId(tenantId)
			.setUserId(cmd.getUserId())
			.setCoreFlowStatus(CoreFlowStatus.PENDING_PAYMENT.name())
			.setPaymentStatus(PaymentStatus.CREATED.name())
			.setFulfillmentStatus(FulfillmentStatus.NONE.name())
			.setAfterSaleStatus(AfterSaleStatus.NONE.name())
			.setTotalAmount(totalAmountCents)
			.setDiscountAmount(discountAmountCents)
			.setPayableAmount(payableAmountCents)
			.setCurrency(total.currency());
		mainEntity.setAddressId(cmd.getAddressId());
		mainEntity.setPromotionQuoteId(quoteResp.getQuoteId());
		mainEntity.setPromotionInputHash(snapshot.getVersion() != null ? snapshot.getVersion().getInputHash() : null);
		orderMainJpaRepository.save(mainEntity);

		Map<String, String> merchantSubOrderNo = new HashMap<>();
		List<OrderSubEntity> subEntities = new ArrayList<>();
		int subIndex = 1;
		for (var merchantSkus : cmd.getMerchantSkus()) {
			String subOrderNo = generateSubOrderNo(mainOrderNo, subIndex++);
			merchantSubOrderNo.put(merchantSkus.merchantId(), subOrderNo);
			subEntities.add(new OrderSubEntity()
				.setSubOrderNo(subOrderNo)
				.setMainOrderNo(mainOrderNo)
				.setTenantId(tenantId)
				.setMerchantId(merchantSkus.merchantId())
				.setMerchantName(null)
				.setCoreFlowStatus(CoreFlowStatus.PENDING_PAYMENT.name())
				.setPaymentStatus(PaymentStatus.CREATED.name())
				.setFulfillmentStatus(FulfillmentStatus.NONE.name())
				.setAfterSaleStatus(AfterSaleStatus.NONE.name())
			);
		}
		orderSubJpaRepository.saveAll(subEntities);

		List<OrderItemEntity> itemEntities = new ArrayList<>();
		int lineNo = 1;
		for (var merchantSkus : cmd.getMerchantSkus()) {
			String subOrderNo = merchantSubOrderNo.get(merchantSkus.merchantId());
			for (var item : merchantSkus.skuItems()) {
				int qty = item.quantity() != null ? item.quantity() : 0;
				var priced = findSnapshotLine(snapshot, merchantSkus.merchantId(), item.skuId());
				long unit = priced != null ? priced.getFinalUnitPriceCents() : 0L;
				long lineTotal = priced != null ? priced.getLineSubtotalCents() : unit * (long) qty;
				long lineDiscount = priced != null ? priced.getLineDiscountAllocatedCents() : 0L;
				long linePayable = priced != null ? priced.getLinePayableCents() : (lineTotal - lineDiscount);
				Map<String, Object> skuSnapshot = new HashMap<>();
				skuSnapshot.put("skuId", item.skuId());
				skuSnapshot.put("merchantId", merchantSkus.merchantId());
				SkuDTO sku = skuMap.get(item.skuId());
				skuSnapshot.put("title", sku != null && sku.getSkuName() != null ? sku.getSkuName() : item.skuId());
				skuSnapshot.put("specJson", sku != null ? sku.getSecJson() : null);
				skuSnapshot.put("unitPriceCents", unit);
				skuSnapshot.put("currency", total.currency());
				skuSnapshot.put("snapshotTime", Instant.now().toString());
				String snapshotJson = objectMapper.writeValueAsString(skuSnapshot);
				itemEntities.add(new OrderItemEntity()
					.setMainOrderNo(mainOrderNo)
					.setSubOrderNo(subOrderNo)
					.setLineNo(lineNo++)
					.setSkuId(item.skuId())
					.setQuantity(qty)
					.setUnitPriceAmount(unit)
					.setLineTotalAmount(lineTotal)
					.setLineDiscountAmount(lineDiscount)
					.setLinePayableAmount(linePayable)
					.setCurrency(total.currency())
					.setSkuSnapshot(snapshotJson)
				);
			}
		}
		orderItemJpaRepository.saveAll(itemEntities);

		OrderItem agg = new OrderItem();
		agg.setOrderNo(mainOrderNo);
		agg.setTenantId(tenantId);
		agg.setUserId(cmd.getUserId());
		agg.setOrderStatus(new OrderStatus(
			CoreFlowStatus.PENDING_PAYMENT,
			PaymentStatus.CREATED,
			FulfillmentStatus.NONE,
			AfterSaleStatus.NONE
		));
		agg.setTotal(total);
		agg.setPayable(payable);
		agg.setVersion(0);
		orderDomainService.recordOutbox(agg, OrderEventTypeConstants.ORDER_CREATED, Map.of(
			"mainOrderNo", mainOrderNo,
			"userId", cmd.getUserId(),
			"merchantCount", cmd.getMerchantSkus() != null ? cmd.getMerchantSkus().size() : 0,
			"lineCount", itemEntities.size()
		));

		SubmitOrderResult result = new SubmitOrderResult();
		// set main order no into result
		result.setMainOrderNo(ono);
		result.setLines(shopLines);
		result.setSummary(new com.github.spud.tinystore.order.application.result.ConfirmOrderResult.OrderSummary(total, payable, java.util.List.of(), java.util.List.of(), java.util.List.of()));

		// save idempotency result if key provided (best-effort)
		if (cmd.getIdempotentKey() != null && !cmd.getIdempotentKey().isBlank()) {
			try {
				com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse resp = new com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse();
				resp.setMainOrderNo(ono.value());
				idempotencyStorage.saveResponse(cmd.getIdempotentKey(), resp);
			} catch (Exception e) {
				log.debug("Failed to save idempotency result: {}", e.getMessage());
			}
		}

		return result;
	}

	/**
	 * 支付成功回调处理（统一使用 PaymentSucceededCommand）
	 */
	public void onPaymentSuccess(PaymentSucceededCommand cmd) {
		String amountStr = cmd.getAmount() != null ? String.valueOf(cmd.getAmount().amount()) : "";
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forPaymentCallback(cmd.getPaymentId(), cmd.getOrderId(), amountStr);
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", java.time.Duration.ofHours(1))) {
			log.info("Payment callback already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("payment.success");
			auditService.record(cmd.getOrderId(), "payment.success", "SYSTEM", cmd.getPaymentId(), true,
				Map.of("idempotent", true, "amount", amountStr));
			return;
		}

		try {
			log.info("Processing payment success for order: {}, paymentId: {}, amount: {}, deposit: {}, final: {}",
				cmd.getOrderId(), cmd.getPaymentId(),
				cmd.getAmount() != null ? cmd.getAmount().amount() : null,
				cmd.isDeposit(), cmd.isFinalPayment());

			OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
			long expectedVersion = order.getVersion();

			OrderMainEntity main = orderMainJpaRepository.findForUpdateByOrderNo(order.getOrderNo()).orElse(null);
			if (main != null && main.getPromotionQuoteId() != null && !main.getPromotionQuoteId().isBlank()) {
				CheckoutCommitRequest commitReq = new CheckoutCommitRequest();
				commitReq.setQuoteId(main.getPromotionQuoteId());
				commitReq.setOrderNo(main.getOrderNo());
				commitReq.setInputHash(main.getPromotionInputHash());
				commitReq.setPayNo(cmd.getPaymentId());
				CheckoutCommitResponse commitResp = promotionService.checkoutCommit(
					buildPromotionIdempotencyKey(idempotencyKey, "commit", main.getOrderNo()),
					commitReq
				);
				if (commitResp == null || commitResp.getStatus() == null) {
					throw new IllegalStateException("promotion commit failed");
				}
				if (commitResp.getStatus().name().startsWith("REQUOTE")) {
					throw new IllegalStateException("promotion requote required after payment");
				}
				if (commitResp.getSnapshot() != null) {
					long expectedPayable = commitResp.getSnapshot().getPayableCents();
					long paid = cmd.getAmount() != null ? cmd.getAmount().amount() : 0L;
					if (expectedPayable != paid) {
						throw new IllegalStateException("paid amount mismatch with promotion snapshot");
					}
					applyPromotionSnapshotToOrder(main, commitResp);
				}
			}
			OrderStatus cur = order.getOrderStatus();
			OrderStatus next = new OrderStatus(
				CoreFlowStatus.PAID,
				PaymentStatus.PAID,
				cur != null ? cur.getFulfillmentStatus() : FulfillmentStatus.NONE,
				cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
			);
			order.setOrderStatus(next);
			orderDomainService.save(order, expectedVersion);
			orderDomainService.recordOutbox(order, OrderEventTypeConstants.PAYMENT_SUCCEEDED, cmd);
			orderMetrics.processed("payment.success");
			auditService.record(cmd.getOrderId(), "payment.success", "SYSTEM", cmd.getPaymentId(), true,
				Map.of("deposit", cmd.isDeposit(), "final", cmd.isFinalPayment(), "amount", amountStr));

			log.info("Payment success processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("payment.success");
			auditService.record(cmd.getOrderId(), "payment.success", "SYSTEM", cmd.getPaymentId(), false,
				Map.of("error", e.getMessage()));
			throw e;
		}
	}

	/**
	 * Auto complete order
	 */
	@Transactional
	public void autoComplete(AutoCompleteCommand cmd) {
		log.info("Auto completing order: {}", cmd.getOrderId());
		String scheduled = cmd.getEventId() != null ? cmd.getEventId() : String.valueOf(cmd.getScheduledAt());
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator.forContentHash("auto_complete",
			cmd.getOrderId(), String.valueOf(cmd.getGraceDays()), scheduled);
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Auto-complete already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("auto.complete");
			auditService.record(cmd.getOrderId(), "auto.complete", "SYSTEM", "scheduler", true,
				Map.of("idempotent", true, "eventId", cmd.getEventId()));
			return;
		}

		try {
			OrderItem order = orderRepository.findById(cmd.getOrderId())
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));

			order.onAutoComplete();

			List<OrderDomainEvent> events = order.pullDomainEvents();
			List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
			orderRepository.saveWithOutbox(order, envelopes);
			orderMetrics.processed("auto.complete");
			auditService.record(cmd.getOrderId(), "auto.complete", "SYSTEM", "scheduler", true,
				Map.of("graceDays", cmd.getGraceDays(), "scheduledAt", cmd.getScheduledAt()));
			log.info("Auto complete processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("auto.complete");
			auditService.record(cmd.getOrderId(), "auto.complete", "SYSTEM", "scheduler", false,
				Map.of("error", e.getMessage()));
			throw e;
		}
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
			case ORDER_PAID -> OrderEventTypeConstants.PAYMENT_SUCCEEDED;
			case ORDER_SHIPPED -> OrderEventTypeConstants.GOODS_SHIPPED;
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
		recordAudit("user.cancel", null, null, true, null);
		if (cmd != null && cmd.getOrderId() != null) {
			OrderItem order = orderDomainService.loadForUpdate(String.valueOf(cmd.getOrderId()));
			releasePromotionIfPresent(cmd.getIdempotencyKey(), order.getOrderNo(), cmd.getReasonCode());
		}
		String result = "待确认";
		recordAudit("user.cancel", null, null, true, null);
		return result;
	}

	// ==================== 新增的订单主流程接口方法 ====================



	/**
	 * 商家接单
	 */
	public void merchantAccept(MerchantAcceptCommand cmd) {
		log.info("Merchant accepting order: {}, operator: {}", cmd.getOrderId(), cmd.getOperatorId());
		recordAudit("merchant.accept", cmd.getOrderId(), cmd.getOperatorId(), true, null);

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		OrderStatus cur = order.getOrderStatus();
		OrderStatus next = new OrderStatus(
			CoreFlowStatus.ACCEPTED,
			cur != null ? cur.getPaymentStatus() : PaymentStatus.PAID,
			cur != null ? cur.getFulfillmentStatus() : FulfillmentStatus.NONE,
			cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
		);
		order.setOrderStatus(next);
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Merchant accepted", cmd.getOperatorId(), cmd.getRequestId());

		orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_ACCEPTED, cmd);
		orderMetrics.processed("merchant.accept");
		auditService.record(cmd.getOrderId(), "merchant.accept", "MERCHANT", cmd.getOperatorId(), true, Map.of());
		recordAudit("merchant.accept", cmd.getOrderId(), cmd.getOperatorId(), true, null);

		log.info("Merchant accept processed for order: {}", cmd.getOrderId());
	}

	/**
	 * 商家发货
	 */
	public void shipOrder(ShipOrderCommand cmd) {
		log.info("Shipping order: {}, operator: {}, logistics: {}",
			cmd.getOrderId(), cmd.getOperatorId(), cmd.getLogistics().getCompanyName());
		recordAudit("order.ship", cmd.getOrderId(), cmd.getOperatorId(), true, null);

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		OrderStatus cur = order.getOrderStatus();
		OrderStatus next = new OrderStatus(
			CoreFlowStatus.FULFILLING,
			cur != null ? cur.getPaymentStatus() : PaymentStatus.PAID,
			FulfillmentStatus.SHIPPED,
			cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
		);
		order.setOrderStatus(next);
		String mainOrderNo = order.getOrderNo();
		List<OrderSubEntity> subs = orderSubJpaRepository.findByMainOrderNo(mainOrderNo);
		OffsetDateTime shippedAt = OffsetDateTime.now();
		for (OrderSubEntity s : subs) {
			s.setLogisticsCompanyCode(cmd.getLogistics().getCompanyCode());
			s.setLogisticsCompanyName(cmd.getLogistics().getCompanyName());
			s.setTrackingNo(cmd.getLogistics().getTrackingNo());
			s.setShippedAt(shippedAt);
			s.setFulfillmentStatus(FulfillmentStatus.SHIPPED.name());
			s.setCoreFlowStatus(CoreFlowStatus.FULFILLING.name());
			s.setPaymentStatus(next.getPaymentStatus().name());
			s.setAfterSaleStatus(next.getAfterSaleStatus().name());
		}
		if (!subs.isEmpty()) {
			orderSubJpaRepository.saveAll(subs);
		}
		orderDomainService.save(order, expectedVersion);

		// 5. 追加状态日志 - 待实现 Order.getId()
		// orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
		//	"Order shipped", cmd.getOperatorId(), cmd.getRequestId());

		orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_SHIPPED, cmd);
		orderMetrics.processed("order.ship");
		auditService.record(cmd.getOrderId(), "order.ship", "MERCHANT", cmd.getOperatorId(), true,
			Map.of("company", cmd.getLogistics().getCompanyName()));
		recordAudit("order.ship", cmd.getOrderId(), cmd.getOperatorId(), true, null);

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
		String timestamp = cmd.getDeliveredAt() != null ? String.valueOf(cmd.getDeliveredAt()) : cmd.getEventId();
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forLogisticsCallback(cmd.getOrderId(), "DELIVERED", timestamp);
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Logistics delivered already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("logistics.delivered");
			auditService.record(cmd.getOrderId(), "logistics.delivered", "SYSTEM", cmd.getSource(), true,
				Map.of("idempotent", true, "eventId", cmd.getEventId()));
			return;
		}
		try {
			OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
			long expectedVersion = order.getVersion();
			OrderStatus cur = order.getOrderStatus();
			OrderStatus next = new OrderStatus(
				CoreFlowStatus.FULFILLING,
				cur != null ? cur.getPaymentStatus() : PaymentStatus.PAID,
				FulfillmentStatus.DELIVERED,
				cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
			);
			order.setOrderStatus(next);
			String mainOrderNo = order.getOrderNo();
			List<OrderSubEntity> subs = orderSubJpaRepository.findByMainOrderNo(mainOrderNo);
			OffsetDateTime deliveredAt = cmd.getDeliveredAt() != null
				? OffsetDateTime.ofInstant(Instant.ofEpochMilli(cmd.getDeliveredAt()), ZoneOffset.UTC)
				: OffsetDateTime.now();
			for (OrderSubEntity s : subs) {
				if (cmd.getTrackingNo() != null && !cmd.getTrackingNo().isBlank() && s.getTrackingNo() != null
					&& !cmd.getTrackingNo().equals(s.getTrackingNo())) {
					continue;
				}
				s.setDeliveredAt(deliveredAt);
				s.setFulfillmentStatus(FulfillmentStatus.DELIVERED.name());
				s.setCoreFlowStatus(CoreFlowStatus.FULFILLING.name());
				s.setPaymentStatus(next.getPaymentStatus().name());
				s.setAfterSaleStatus(next.getAfterSaleStatus().name());
			}
			if (!subs.isEmpty()) {
				orderSubJpaRepository.saveAll(subs);
			}
			orderDomainService.save(order, expectedVersion);
			orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_DELIVERED, cmd);
			orderMetrics.processed("logistics.delivered");
			auditService.record(cmd.getOrderId(), "logistics.delivered", "SYSTEM", cmd.getSource(), true,
				Map.of("trackingNo", cmd.getTrackingNo(), "deliveredAt", cmd.getDeliveredAt()));
			log.info("Logistics delivery processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("logistics.delivered");
			auditService.record(cmd.getOrderId(), "logistics.delivered", "SYSTEM", cmd.getSource(), false,
				Map.of("error", e.getMessage()));
			throw e;
		}
	}

	/**
	 * 商家侧妥投确认（与内部妥投回调保持一致的幂等/审计/指标语义）
	 */
	public void confirmDelivered(DeliveredCommand cmd) {
		log.info("Merchant confirming delivery for order: {}, trackingNo: {}", cmd.getOrderId(), cmd.getTrackingNo());
		recordAudit("merchant.delivery_confirm", cmd.getOrderId(), cmd.getSource(), true, null);
		String timestamp = cmd.getDeliveredAt() != null ? String.valueOf(cmd.getDeliveredAt()) : cmd.getEventId();
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
			.forLogisticsCallback(cmd.getOrderId(), "DELIVERED", timestamp);
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Merchant delivery confirm already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("merchant.delivery_confirm");
			auditService.record(cmd.getOrderId(), "merchant.delivery_confirm", "MERCHANT", cmd.getSource(), true,
				Map.of("idempotent", true, "eventId", cmd.getEventId()));
			return;
		}
		try {
			OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
			long expectedVersion = order.getVersion();
			OrderStatus cur = order.getOrderStatus();
			OrderStatus next = new OrderStatus(
				CoreFlowStatus.FULFILLING,
				cur != null ? cur.getPaymentStatus() : PaymentStatus.PAID,
				FulfillmentStatus.DELIVERED,
				cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
			);
			order.setOrderStatus(next);
			String mainOrderNo = order.getOrderNo();
			List<OrderSubEntity> subs = orderSubJpaRepository.findByMainOrderNo(mainOrderNo);
			OffsetDateTime deliveredAt = cmd.getDeliveredAt() != null
				? OffsetDateTime.ofInstant(Instant.ofEpochMilli(cmd.getDeliveredAt()), ZoneOffset.UTC)
				: OffsetDateTime.now();
			for (OrderSubEntity s : subs) {
				if (cmd.getTrackingNo() != null && !cmd.getTrackingNo().isBlank() && s.getTrackingNo() != null
					&& !cmd.getTrackingNo().equals(s.getTrackingNo())) {
					continue;
				}
				s.setDeliveredAt(deliveredAt);
				s.setFulfillmentStatus(FulfillmentStatus.DELIVERED.name());
				s.setCoreFlowStatus(CoreFlowStatus.FULFILLING.name());
				s.setPaymentStatus(next.getPaymentStatus().name());
				s.setAfterSaleStatus(next.getAfterSaleStatus().name());
			}
			if (!subs.isEmpty()) {
				orderSubJpaRepository.saveAll(subs);
			}
			orderDomainService.save(order, expectedVersion);
			orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_DELIVERED, cmd);
			orderMetrics.processed("merchant.delivery_confirm");
			auditService.record(cmd.getOrderId(), "merchant.delivery_confirm", "MERCHANT", cmd.getSource(), true,
				Map.of("trackingNo", cmd.getTrackingNo(), "deliveredAt", cmd.getDeliveredAt()));
			recordAudit("merchant.delivery_confirm", cmd.getOrderId(), cmd.getSource(), true, null);
			log.info("Merchant delivery confirm processed for order: {}", cmd.getOrderId());
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("merchant.delivery_confirm");
			auditService.record(cmd.getOrderId(), "merchant.delivery_confirm", "MERCHANT", cmd.getSource(), false,
				Map.of("error", e.getMessage()));
			recordAudit("merchant.delivery_confirm", cmd.getOrderId(), cmd.getSource(), false, e.getMessage());
			throw e;
		}
	}

	/**
	 * 用户确认收货
	 */
	public void confirmReceipt(ConfirmReceiptCommand cmd) {
		log.info("User confirming receipt for order: {}, user: {}", cmd.getOrderId(), cmd.getUserId());
		recordAudit("user.confirm_receipt", cmd.getOrderId(), cmd.getUserId(), true, null);

		// 1. 加载订单 (load-for-update with version)
		OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
		long expectedVersion = order.getVersion();

		OrderStatus cur = order.getOrderStatus();
		OrderStatus next = new OrderStatus(
			CoreFlowStatus.COMPLETED,
			cur != null ? cur.getPaymentStatus() : PaymentStatus.PAID,
			FulfillmentStatus.RECEIVED,
			cur != null ? cur.getAfterSaleStatus() : AfterSaleStatus.NONE
		);
		order.setOrderStatus(next);
		String mainOrderNo = order.getOrderNo();
		List<OrderSubEntity> subs = orderSubJpaRepository.findByMainOrderNo(mainOrderNo);
		OffsetDateTime receivedAt = OffsetDateTime.now();
		for (OrderSubEntity s : subs) {
			s.setReceivedAt(receivedAt);
			s.setFulfillmentStatus(FulfillmentStatus.RECEIVED.name());
			s.setCoreFlowStatus(CoreFlowStatus.COMPLETED.name());
			s.setPaymentStatus(next.getPaymentStatus().name());
			s.setAfterSaleStatus(next.getAfterSaleStatus().name());
		}
		if (!subs.isEmpty()) {
			orderSubJpaRepository.saveAll(subs);
		}
		orderDomainService.save(order, expectedVersion);
		orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_RECEIVED, cmd);
		orderMetrics.processed("user.confirm_receipt");
		auditService.record(cmd.getOrderId(), "user.confirm_receipt", "USER", cmd.getUserId(), true, Map.of());
		recordAudit("user.confirm_receipt", cmd.getOrderId(), cmd.getUserId(), true, null);

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
		recordAudit("user.apply_aftersale", String.valueOf(cmd.getOrderId()), cmd.getUserId(), true, null);
		String ticketId = UUID.randomUUID().toString();
		recordAudit("user.apply_aftersale", String.valueOf(cmd.getOrderId()), cmd.getUserId(), true, null);
		return ticketId; // 返回售后单ID
	}

	private void recordAudit(String commandName, String orderId, String actorId, boolean success, String errorCode) {
		AuditRecorder rec = auditRecorderProvider != null ? auditRecorderProvider.getIfAvailable(() -> null) : null;
		if (rec == null) return;
		String traceId = org.slf4j.MDC.get("traceId");
		String tenantId = org.slf4j.MDC.get("tenantId");
		AuditEntry entry = new AuditEntry(System.currentTimeMillis(), commandName, orderId, actorId,
			tenantId, traceId, success, errorCode);
		try { rec.record(entry); } catch (Exception ignored) {}
	}

	private String generateSubOrderNo(String mainOrderNo, int index) {
		String suffix = UUID.randomUUID().toString().replace("-", "");
		suffix = suffix.substring(0, 8);
		String base = mainOrderNo;
		if (base == null) {
			base = "";
		}
		if (base.length() > 30) {
			base = base.substring(base.length() - 30);
		}
		String no = "SUB-" + base + "-" + index + "-" + suffix;
		if (no.length() > 64) {
			no = no.substring(0, 64);
		}
		return no;
	}

	/**
	 * 退款成功回调处理
	 */
		public void onRefundSuccess(RefundSucceededCommand cmd) {
			String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
				.forRefundCallback(cmd.getRefundId(), cmd.getOrderId());
			if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
				log.info("Refund callback already processed: {}", idempotencyKey);
				orderMetrics.idempotencyConflict("refund.success");
				auditService.record(cmd.getOrderId(), "refund.success", "SYSTEM", cmd.getRefundId(), true,
					Map.of("idempotent", true));
				return;
			}
			try {
				log.info("Processing refund success for order: {}, refundId: {}", cmd.getOrderId(), cmd.getRefundId());
				OrderItem order = orderDomainService.loadForUpdate(cmd.getOrderId());
				long expectedVersion = order.getVersion();
				// 占位：后续补齐维度状态更新
				orderDomainService.save(order, expectedVersion);
				orderDomainService.recordOutbox(order, "order.refund.completed", cmd);
				orderMetrics.processed("refund.success");
				auditService.record(cmd.getOrderId(), "refund.success", "SYSTEM", cmd.getRefundId(), true,
					Map.of());
			} catch (Exception e) {
				idempotencyRepository.release(idempotencyKey, "order-service");
				orderMetrics.failure("refund.success");
				auditService.record(cmd.getOrderId(), "refund.success", "SYSTEM", cmd.getRefundId(), false,
					Map.of("error", e.getMessage()));
				throw e;
			}
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
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator.forContentHash(
			"timeout_cancel", String.valueOf(cmd.getOrderId()), cmd.getScheduleId(), cmd.getEventId());
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Timeout cancel already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("timeout.unpaid_cancel");
			auditService.record(String.valueOf(cmd.getOrderId()), "timeout.unpaid_cancel", "SYSTEM", "scheduler", true,
				Map.of("idempotent", true, "scheduleId", cmd.getScheduleId()));
			return;
		}
		try {
			log.info("Timeout cancelling order: {}, schedule: {}", cmd.getOrderId(), cmd.getScheduleId());
			OrderItem order = orderDomainService.loadForUpdate(String.valueOf(cmd.getOrderId()));
			long expectedVersion = order.getVersion();
			releasePromotionIfPresent(idempotencyKey, order.getOrderNo(), "PAYMENT_TIMEOUT");
			// 占位：状态机与资源释放逻辑待补齐
			orderDomainService.save(order, expectedVersion);
			orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_CANCELLED, Map.of("reason", "PAYMENT_TIMEOUT"));
			orderMetrics.processed("timeout.unpaid_cancel");
			auditService.record(String.valueOf(cmd.getOrderId()), "timeout.unpaid_cancel", "SYSTEM", "scheduler", true,
				Map.of("scheduleId", cmd.getScheduleId()));
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("timeout.unpaid_cancel");
			auditService.record(String.valueOf(cmd.getOrderId()), "timeout.unpaid_cancel", "SYSTEM", "scheduler", false,
				Map.of("error", e.getMessage()));
			throw e;
		}
	}

	/**
	 * 转待履约（保障性作业）
	 */
	public void moveToAwaitFulfillment(MoveToAwaitFulfillmentCommand cmd) {
		String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator.forContentHash(
			"await_fulfillment", String.valueOf(cmd.getOrderId()), cmd.getEventId());
		if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
			log.info("Await-fulfillment already processed: {}", idempotencyKey);
			orderMetrics.idempotencyConflict("auto.await_fulfillment");
			auditService.record(String.valueOf(cmd.getOrderId()), "auto.await_fulfillment", "SYSTEM", "scheduler", true,
				Map.of("idempotent", true, "eventId", cmd.getEventId()));
			return;
		}
		try {
			log.info("Moving order to await fulfillment: {}", cmd.getOrderId());
			OrderItem order = orderDomainService.loadForUpdate(String.valueOf(cmd.getOrderId()));
			long expectedVersion = order.getVersion();
			// 占位：状态机推进到待履约态
			orderDomainService.save(order, expectedVersion);
			orderDomainService.recordOutbox(order, OrderEventTypeConstants.ORDER_LIFECYCLE_CHANGED, Map.of("to", "AWAIT_FULFILLMENT"));
			orderMetrics.processed("auto.await_fulfillment");
			auditService.record(String.valueOf(cmd.getOrderId()), "auto.await_fulfillment", "SYSTEM", "scheduler", true,
				Map.of("eventId", cmd.getEventId()));
		} catch (Exception e) {
			idempotencyRepository.release(idempotencyKey, "order-service");
			orderMetrics.failure("auto.await_fulfillment");
			auditService.record(String.valueOf(cmd.getOrderId()), "auto.await_fulfillment", "SYSTEM", "scheduler", false,
				Map.of("error", e.getMessage()));
			throw e;
		}
	}

	public boolean validateOrderParam(String userId, List<String> productIds, Set<String> couponIds,
		String addressId) {

		return true;
	}

	// -------------------------- 工具方法 --------------------------
	private String buildPromotionIdempotencyKey(String base, String operation, String stableId) {
		String op = operation == null ? "op" : operation;
		if (base == null || base.isBlank()) {
			return "order:promotion:" + op + ":" + stableId;
		}
		return base + ":promotion:" + op;
	}

	private CheckoutQuoteRequest buildCheckoutQuoteRequest(SubmitOrderCommand cmd, Map<String, SkuDTO> skuMap) {
		CheckoutQuoteRequest req = new CheckoutQuoteRequest();
		req.setUserId(cmd.getUserId());
		req.setTraceId(null);
		req.setAddressId(cmd.getAddressId());

		List<CheckoutQuoteRequest.Line> lines = new ArrayList<>();
		Map<String, List<String>> shopCoupons = new HashMap<>();
		for (MerchantSkuDTO g : cmd.getMerchantSkus()) {
			if (g.merchantCouponId() != null && !g.merchantCouponId().isBlank()) {
				shopCoupons.put(g.merchantId(), List.of(g.merchantCouponId()));
			}
			for (ConfirmOrderCommand.SkuItemDTO it : g.skuItems()) {
				SkuDTO sku = skuMap.get(it.skuId());
				CheckoutQuoteRequest.Line l = new CheckoutQuoteRequest.Line();
				l.setSkuId(it.skuId());
				l.setShopId(g.merchantId());
				l.setQuantity(it.quantity());
				l.setBaseUnitPriceCents(sku != null ? sku.getUnitPrice() : 0L);
				l.setWeightGrams(sku != null ? kgToGrams(sku.getWeight()) : 0L);
				lines.add(l);
			}
		}
		req.setLines(lines);

		CheckoutQuoteRequest.AppliedIntent intent = new CheckoutQuoteRequest.AppliedIntent();
		if (cmd.getPlatformCouponId() != null && !cmd.getPlatformCouponId().isBlank()) {
			intent.setPlatformCouponIds(List.of(cmd.getPlatformCouponId()));
		} else {
			intent.setPlatformCouponIds(List.of());
		}
		intent.setShopCouponIdsByShop(shopCoupons);
		req.setAppliedIntent(intent);
		return req;
	}

	private List<ConfirmOrderResult.ShopProductSnapshot> buildShopLines(List<MerchantSkuDTO> merchantSkus,
		com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot snapshot, String currency) {
		List<ConfirmOrderResult.ShopProductSnapshot> shopLines = new ArrayList<>();
		for (MerchantSkuDTO g : merchantSkus) {
			List<ConfirmOrderResult.ProductSnapshot> products = new ArrayList<>();
			Money shopTotal = Money.ofCents(0L, currency);
			for (com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot.PricedLine l : snapshot.getLines()) {
				if (!g.merchantId().equals(l.getShopId())) {
					continue;
				}
				Money unit = Money.ofCents(l.getFinalUnitPriceCents(), currency);
				Money linePayable = Money.ofCents(l.getLinePayableCents(), currency);
				products.add(new ConfirmOrderResult.ProductSnapshot(l.getSkuId(), unit, linePayable, l.getQuantity()));
				shopTotal = shopTotal.add(linePayable);
			}
			shopLines.add(new ConfirmOrderResult.ShopProductSnapshot(g.merchantId(), products, shopTotal));
		}
		return shopLines;
	}

	private com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot.PricedLine findSnapshotLine(
		com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot snapshot, String shopId,
		String skuId) {
		if (snapshot == null || snapshot.getLines() == null) {
			return null;
		}
		for (com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot.PricedLine l : snapshot.getLines()) {
			if (shopId.equals(l.getShopId()) && skuId.equals(l.getSkuId())) {
				return l;
			}
		}
		return null;
	}

	private void applyPromotionSnapshotToOrder(OrderMainEntity main, CheckoutCommitResponse commitResp) {
		com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot snapshot = commitResp.getSnapshot();
		if (snapshot == null) {
			return;
		}
		main.setPromotionQuoteId(commitResp.getFinalQuoteId());
		main.setPromotionInputHash(snapshot.getVersion() != null ? snapshot.getVersion().getInputHash() : null);
		main.setTotalAmount(snapshot.getItemsTotalCents() + snapshot.getShippingFeeCents());
		main.setDiscountAmount(snapshot.getPromotionDiscountTotalCents() + snapshot.getCouponDiscountTotalCents());
		main.setPayableAmount(snapshot.getPayableCents());
		orderMainJpaRepository.save(main);

		Map<String, String> subToMerchant = new HashMap<>();
		for (OrderSubEntity s : orderSubJpaRepository.findByMainOrderNo(main.getOrderNo())) {
			if (s.getSubOrderNo() != null && s.getMerchantId() != null) {
				subToMerchant.put(s.getSubOrderNo(), s.getMerchantId());
			}
		}
		List<OrderItemEntity> items = orderItemJpaRepository.findByMainOrderNoOrderByLineNoAsc(main.getOrderNo());
		for (OrderItemEntity it : items) {
			String shopId = subToMerchant.get(it.getSubOrderNo());
			if (shopId == null) {
				continue;
			}
			com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot.PricedLine pl = null;
			for (com.github.spud.tinystore.order.infrastructure.acl.PromotionClient.PricingSnapshot.PricedLine l : snapshot.getLines()) {
				if (shopId.equals(l.getShopId()) && it.getSkuId().equals(l.getSkuId())) {
					pl = l;
					break;
				}
			}
			if (pl == null) {
				continue;
			}
			it.setUnitPriceAmount(pl.getFinalUnitPriceCents());
			it.setLineTotalAmount(pl.getLineSubtotalCents());
			it.setLineDiscountAmount(pl.getLineDiscountAllocatedCents());
			it.setLinePayableAmount(pl.getLinePayableCents());
		}
		if (!items.isEmpty()) {
			orderItemJpaRepository.saveAll(items);
		}
	}

	private void releasePromotionIfPresent(String idempotencyKey, String orderNo, String reason) {
		if (orderNo == null || orderNo.isBlank()) {
			return;
		}
		OrderMainEntity main = orderMainJpaRepository.findForUpdateByOrderNo(orderNo).orElse(null);
		if (main == null || main.getPromotionQuoteId() == null || main.getPromotionQuoteId().isBlank()) {
			return;
		}
		CheckoutReleaseRequest req = new CheckoutReleaseRequest();
		req.setQuoteId(main.getPromotionQuoteId());
		req.setOrderNo(orderNo);
		req.setReason(reason);
		promotionService.checkoutRelease(buildPromotionIdempotencyKey(idempotencyKey, "release", orderNo), req);
	}

	private long kgToGrams(double kg) {
		long g = Math.round(kg * 1000.0d);
		return Math.max(0L, g);
	}

	private void validateSkuAndMerchantConsistency(
		List<MerchantSkuDTO> merchantSkus,
		Map<String, SkuDTO> skuMap
	) throws Exception {
		for (var merchantSku : merchantSkus) {
			String requestMerchantId = merchantSku.merchantId();
			for (ConfirmOrderCommand.SkuItemDTO item : merchantSku.skuItems()) {
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
			// 占位实现：未接入运费计算时返回0
			return Money.of(0);
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
		// TOOD: 填充 response 字段
		return response;
	}
}
