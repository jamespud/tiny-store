package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.domain.enums.MainOrderStatus;
import com.github.spud.tinystore.order.domain.enums.SubOrderStatus;
import com.github.spud.tinystore.order.domain.event.DomainEventPublisher;
import com.github.spud.tinystore.order.domain.event.MultiShopOrderCreatedEvent;
import com.github.spud.tinystore.order.domain.model.MainOrder;
import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import com.github.spud.tinystore.order.domain.model.SubOrderItem;
import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
import com.github.spud.tinystore.order.domain.repository.MultiShopAmountCalculateService;
import com.github.spud.tinystore.order.domain.repository.MultiShopOrderRepository;
import com.github.spud.tinystore.order.domain.service.TenantContext;
import com.github.spud.tinystore.order.domain.status.OrderStatus;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.LogisticsFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.MerchantFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.NotificationFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.PaymentFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.ProductFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionFeignClient;
import com.github.spud.tinystore.order.infrastructure.acl.RiskControlFeignClient;
import com.github.spud.tinystore.order.interfaces.dto.request.CreateOrderRequest;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateOrderResponse;
import com.github.spud.tinystore.order.interfaces.error.OrderBusinessException;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 多店铺订单应用服务（核心处理多商家SKU拆分、跨店优惠分摊、合并支付）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserOrderApplicationService {

	// 外部服务Feign客户端
	private final ProductFeignClient productFeignClient;
	private final InventoryFeignClient inventoryFeignClient;
	private final PromotionFeignClient promotionFeignClient;
	private final RiskControlFeignClient riskControlFeignClient;
	private final LogisticsFeignClient logisticsFeignClient;
	private final PaymentFeignClient paymentFeignClient;
	private final NotificationFeignClient notificationFeignClient;
	private final MerchantFeignClient merchantFeignClient;

	// 领域服务与仓储
	private final MultiShopOrderRepository multiShopOrderRepository;
	private final MultiShopAmountCalculateService multiShopAmountCalculateService;

	// 事件发布器与幂等存储
	private final DomainEventPublisher eventPublisher;
	private final IdempotencyStorage idempotencyStorage;

	/**
	 * 多店铺创建订单核心流程（事务保证：主订单+子订单要么全成功，要么全回滚）
	 */
	@Transactional(rollbackOn = Exception.class)
	public CreateOrderResponse submitOrder(
		CreateOrderRequest request, String idempotencyKey
	) throws Exception {
		String tenantId = TenantContext.getTenantId();
		String userId = request.getUserId();
		log.info("开始创建多店铺订单：tenantId={}, userId={}, idempotencyKey={}, 商家数={}",
			tenantId, userId, idempotencyKey, request.getMerchantSkuGroups().size());

		// -------------------------- 1. 幂等校验（基于主订单幂等键） --------------------------
		if (idempotencyStorage.exists(idempotencyKey)) {
			log.warn("多店铺订单幂等键已存在：idempotencyKey={}", idempotencyKey);
			return idempotencyStorage.getResponse(idempotencyKey, CreateOrderResponse.class);
		}

		// -------------------------- 2. 前置校验（风控+SKU合法性+商家一致性） --------------------------
		// 2.1 风控检查（多店铺场景需校验“跨店下单是否异常”）
		RiskControlFeignClient.MultiShopRiskCheckResponse riskResponse = riskControlFeignClient.checkMultiShopOrderRisk(
			RiskControlFeignClient.MultiShopRiskCheckRequest.builder()
				.userId(userId)
				.merchantCount(request.getMerchantSkuGroups().size())
				.skuList(this.flattenSkuList(request.getMerchantSkuGroups()))
				.addressId(request.getAddressId())
				.build()
		);
		if (!riskResponse.isPass()) {
			throw OrderBusinessException.riskBlocked(riskResponse.getReason());
		}

		// 2.2 批量查询所有SKU信息（含所属商家ID，校验“请求商家ID与SKU实际商家ID一致”）
		List<String> allSkuIds = this.flattenSkuList(request.getMerchantSkuGroups()).stream()
			.map(RiskControlFeignClient.SkuRiskDTO::getSkuId)
			.toList();
		ProductFeignClient.SkuBatchQueryResponse skuBatchResponse = productFeignClient.batchGetSkuInfo(
			allSkuIds);
		Map<String, ProductFeignClient.SkuDTO> skuMap = skuBatchResponse.getSkuMap();

		// 2.3 校验SKU合法性+商家一致性（请求的商家ID必须与SKU实际商家ID一致）
		this.validateSkuAndMerchantConsistency(request.getMerchantSkuGroups(), skuMap);

		// 2.4 批量查询商家信息（名称、运费政策，用于子订单构建）
		List<String> merchantIds = request.getMerchantSkuGroups().stream()
			.map(CreateOrderRequest.MerchantSkuGroupDTO::getMerchantId)
			.toList();
		MerchantFeignClient.MerchantBatchQueryResponse merchantResponse = merchantFeignClient.batchGetMerchantInfo(
			merchantIds);
		Map<String, MerchantFeignClient.MerchantDTO> merchantMap = merchantResponse.getMerchantMap();

		// -------------------------- 3. 优惠预核销（平台优惠券+商家优惠券） --------------------------
		// 3.1 平台优惠券预核销（跨店可用，仅1张）
		PromotionFeignClient.PreUseCouponResponse platformCouponResponse = null;
		Money platformDiscountTotal = Money.of(0);
		if (request.getPlatformCouponId() != null) {
			platformCouponResponse = promotionFeignClient.preUsePlatformCoupon(
				PromotionFeignClient.PreUsePlatformCouponRequest.builder()
					.userId(userId)
					.couponId(request.getPlatformCouponId())
					.skuList(this.flattenSkuForCoupon(request.getMerchantSkuGroups(), skuMap)) // 带商家ID的SKU列表
					.build()
			);
			if (!platformCouponResponse.isValid()) {
				throw new OrderBusinessException(
					"平台优惠券不可用：" + platformCouponResponse.getInvalidReason(), "ORDER-4004",
					HttpStatus.BAD_REQUEST
				);
			}
			platformDiscountTotal = Money.of(platformCouponResponse.getTotalDiscount());
		}

		// 3.2 商家优惠券预核销
		Map<String, PromotionFeignClient.PreUseCouponResponse> merchantCouponMap = new HashMap<>();
		for (CreateOrderRequest.MerchantSkuGroupDTO merchantGroup : request.getMerchantSkuGroups()) {
			String merchantId = merchantGroup.getMerchantId();
			String merchantCouponId = merchantGroup.getMerchantCouponId();
			if (!StringUtils.hasText(merchantCouponId)) {
				continue;
			}

			// 仅传递当前商家的SKU列表用于核销
			List<PromotionFeignClient.SkuDTO> merchantSkuList = merchantGroup.getSkuItems().stream()
				.map(item -> new PromotionFeignClient.SkuDTO(
					item.getSkuId(),
					item.getQuantity(),
					skuMap.get(item.getSkuId()).getSalePrice()
				))
				.collect(Collectors.toList());

			PromotionFeignClient.PreUseCouponResponse merchantCouponResp = promotionFeignClient.preUseMerchantCoupon(
				PromotionFeignClient.PreUseMerchantCouponRequest.builder()
					.userId(userId)
					.merchantId(merchantId)
					.couponIds(merchantCouponId)
					.skuList(merchantSkuList)
					.build()
			);

			if (!merchantCouponResp.isValid()) {
				// 商家优惠券核销失败，回滚已核销的平台优惠券
				if (platformCouponResponse != null) {
					promotionFeignClient.rollbackCouponUse(platformCouponResponse.getLockId());
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
		List<InventoryFeignClient.StockPreOccupyDTO> allPreOccupyList = request.getMerchantSkuGroups()
			.stream()
			.flatMap(merchantGroup -> merchantGroup.getSkuItems().stream()
				.map(item -> new InventoryFeignClient.StockPreOccupyDTO(
					item.getSkuId(),
					item.getQuantity()
				))
			)
			.collect(Collectors.toList());

		// 4.2 批量预占库存
		InventoryFeignClient.StockPreOccupyResponse stockResponse = inventoryFeignClient.preOccupyStock(
			allPreOccupyList);
		if (!stockResponse.isSuccess()) {
			// 库存不足，回滚所有已核销的优惠券
			this.rollbackAllCoupons(platformCouponResponse, merchantCouponMap);
			throw OrderBusinessException.stockInsufficient(stockResponse.getLackSkuId());
		}
		// 按商家分组存储库存预占ID（后续关联子订单）
		Map<String, String> merchantStockPreOccupyMap = this.groupStockPreOccupyByMerchant(
			request.getMerchantSkuGroups(), skuMap, stockResponse.getPreOccupyIds()
		);

		try {
			// -------------------------- 5. 按商家拆分并构建子订单 --------------------------
			List<SubOrder> subOrderList = new ArrayList<>();
			for (CreateOrderRequest.MerchantSkuGroupDTO merchantGroup : request.getMerchantSkuGroups()) {
				String merchantId = merchantGroup.getMerchantId();
				MerchantFeignClient.MerchantDTO merchantDTO = merchantMap.get(merchantId);
				PromotionFeignClient.PreUseCouponResponse merchantCouponResp = merchantCouponMap.get(
					merchantId);

				// 5.1 构建子订单SKU明细（SubOrderItem）
				List<SubOrderItem> subItemList = merchantGroup.getSkuItems().stream()
					.map(item -> {
						ProductFeignClient.SkuDTO skuDTO = skuMap.get(item.getSkuId());
						return SubOrderItem.builder()
							.id(UUID.randomUUID().toString())
							.subOrderNo(null) // 子订单号后续生成
							.skuId(item.getSkuId())
							.skuName(skuDTO.getSkuName())
							.skuImage(skuDTO.getMainImage())
							.specCombination(skuDTO.getSpecCombination())
							.unitPrice(Money.of(skuDTO.getSalePrice()))
							.quantity(item.getQuantity())
							.itemTotalPrice(Money.of(skuDTO.getSalePrice()).multiply(item.getQuantity()))
							.itemPlatformDiscount(Money.of(0)) // 平台优惠分摊后续计算
							.build();
					})
					.collect(Collectors.toList());

				// 5.2 计算子订单基础金额（商品总价、商家优惠、运费）
				Money subGoodsTotal = subItemList.stream()
					.map(SubOrderItem::getItemTotalPrice)
					.reduce(Money.of(0), Money::add);
				Money subMerchantDiscount = merchantCouponResp != null ?
					Money.of(merchantCouponResp.getTotalDiscount()) : Money.of(0);
				// 计算子订单运费（调用物流服务，传入商家地址、SKU重量）
				Money subFreight = this.calculateMerchantFreight(merchantGroup, subItemList, skuMap,
					merchantDTO);

				// 5.3 暂存子订单基础信息（平台优惠分摊后续统一处理）
				subOrderList.add(SubOrder.builder()
					.id(UUID.randomUUID().toString())
					.orderNo(
						OrderNo.of("DO_S_" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6)))
					.mainOrderNo(null) // 主订单号后续生成
					.merchantId(merchantId)
					.merchantName(merchantDTO.getMerchantName())
					.status(OrderStatus.CREATED)
					.subGoodsTotal(subGoodsTotal)
					.subMerchantDiscount(subMerchantDiscount)
					.subPlatformDiscount(Money.of(0)) // 待分摊
					.subFreight(subFreight)
					.subPayAmount(Money.of(0)) // 待计算（含平台优惠分摊）
					.subItems(subItemList)
//					.merchantCouponId(merchantCouponId)
					.stockPreOccupyIds(merchantStockPreOccupyMap.get(merchantId))
					.couponLockId(this.buildSubCouponLockId(platformCouponResponse, merchantCouponResp))
					.build());
			}

			// -------------------------- 6. 平台优惠分摊（按子订单商品总价占比拆分） --------------------------
			List<SubOrder> subOrdersWithDiscount = multiShopAmountCalculateService.allocatePlatformDiscount(
				subOrderList, platformDiscountTotal
			);

			// -------------------------- 7. 构建主订单 --------------------------
			// 7.1 生成主订单号
			String mainOrderNo =
				"DO_M_" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
			// 7.2 关联主订单号到所有子订单
			subOrdersWithDiscount.forEach(sub -> sub.setMainOrderNo(mainOrderNo));
			// 7.3 计算主订单总实付金额（所有子订单实付金额之和）
			Money totalPayAmount = subOrdersWithDiscount.stream()
				.map(SubOrder::getSubPayAmount)
				.reduce(Money.of(0), Money::add);

			// 7.4 构建主订单实体
			MainOrder mainOrder = MainOrder.builder()
				.id(UUID.randomUUID().toString())
				.mainOrderNo(mainOrderNo)
				.tenantId(tenantId)
				.userId(userId)
				.mainStatus(MainOrderStatus.MERGE_PENDING_PAY)
				.totalPayAmount(totalPayAmount)
//				.payType(request.getPayType())
				.payExpireTime(System.currentTimeMillis() + 1800 * 1000) // 30分钟有效期
				.subOrders(subOrdersWithDiscount)
				.platformCouponId(request.getPlatformCouponId())
				.platformDiscount(platformDiscountTotal)
				.idempotencyKey(idempotencyKey)
				.build();

			// -------------------------- 8. 数据落库（主订单+子订单+明细） --------------------------
			multiShopOrderRepository.saveMultiShopOrder(mainOrder);
			log.info("多店铺订单数据落库成功：mainOrderNo={}, 子订单数={}", mainOrderNo,
				subOrdersWithDiscount.size());

			// -------------------------- 9. 生成合并支付链接（关联主订单+所有子订单） --------------------------
			PaymentFeignClient.CreateMergePayResponse mergePayResponse = paymentFeignClient.createMergePayment(
				PaymentFeignClient.CreateMergePayRequest.builder()
					.mainOrderNo(mainOrderNo)
					.subOrderNos(subOrdersWithDiscount.stream()
						.map(SubOrder::getOrderNo)
						.map(OrderNo::value)
						.collect(Collectors.toList())
					)
					.userId(userId)
					.totalPayAmount(totalPayAmount.amount())
//					.payType(request.getPayType())
					.subject("抖音商城多店铺订单-" + mainOrderNo)
					.build()
			);

			// -------------------------- 10. 幂等缓存（存储主订单响应） --------------------------
			CreateOrderResponse response = this.buildMultiShopOrderResponse(
				mainOrder, subOrdersWithDiscount, mergePayResponse
			);
			idempotencyStorage.save(idempotencyKey, response, 1800); // 缓存30分钟

			// -------------------------- 11. 发布多店铺订单创建事件（通知下游） --------------------------
			eventPublisher.publish(new MultiShopOrderCreatedEvent(
				mainOrderNo,
				tenantId,
				userId,
				subOrdersWithDiscount.stream()
					.map(sub -> new MultiShopOrderCreatedEvent.SubOrderRef(
						sub.getOrderNo().value(),
						sub.getMerchantId(),
						sub.getStockPreOccupyIds(),
						sub.getCouponLockId()
					))
					.collect(Collectors.toList())
			));

			// -------------------------- 12. 发送用户通知（合并下单成功） --------------------------
			notificationFeignClient.sendMultiShopOrderCreateNotice(
				userId,
				mainOrderNo,
				totalPayAmount.amount(),
				subOrdersWithDiscount.size()
			);

			log.info("多店铺订单创建完成：mainOrderNo={}, totalPayAmount={}, 子订单数={}",
				mainOrderNo, totalPayAmount.amount(), subOrdersWithDiscount.size());
			return response;

		} catch (Exception e) {
			// 异常回滚：释放库存+释放所有优惠券
			log.error("创建多店铺订单异常，触发全局回滚：userId={}, error={}", userId, e.getMessage(), e);
			// 回滚库存预占
			inventoryFeignClient.rollbackPreOccupy(stockResponse.getPreOccupyIds());
			// 回滚所有优惠券
			this.rollbackAllCoupons(platformCouponResponse, merchantCouponMap);
			throw e;
		}
	}

	// -------------------------- 工具方法：扁平化SKU列表（用于风控/库存） --------------------------
	private List<RiskControlFeignClient.SkuRiskDTO> flattenSkuList(
		List<CreateOrderRequest.MerchantSkuGroupDTO> merchantGroups
	) {
		return merchantGroups.stream()
			.flatMap(group -> group.getSkuItems().stream()
				.map(item -> new RiskControlFeignClient.SkuRiskDTO(
					item.getSkuId(),
					item.getQuantity()
				))
			)
			.collect(Collectors.toList());
	}

	// -------------------------- 工具方法：校验SKU合法性与商家一致性 --------------------------
	private void validateSkuAndMerchantConsistency(
		List<CreateOrderRequest.MerchantSkuGroupDTO> merchantGroups,
		Map<String, ProductFeignClient.SkuDTO> skuMap
	) throws Exception {
		for (CreateOrderRequest.MerchantSkuGroupDTO group : merchantGroups) {
			String requestMerchantId = group.getMerchantId();
			for (CreateOrderRequest.SkuItemDTO item : group.getSkuItems()) {
				String skuId = item.getSkuId();
				ProductFeignClient.SkuDTO skuDTO = skuMap.get(skuId);

				// 1. 校验SKU存在且已上架
				if (skuDTO == null || !"PUBLISHED".equals(skuDTO.getStatus())) {
					throw OrderBusinessException.skuUnavailable(skuId);
				}

				// 2. 校验请求商家ID与SKU实际商家ID一致
				String actualMerchantId = skuDTO.getMerchantId();
				if (!requestMerchantId.equals(actualMerchantId)) {
					throw new OrderBusinessException(
						"SKU[" + skuId + "]所属商家[" + actualMerchantId + "]与请求商家[" + requestMerchantId
							+ "]不一致",
						"ORDER-4006",
						org.springframework.http.HttpStatus.BAD_REQUEST
					);
				}
			}
		}
	}

	// -------------------------- 工具方法：按商家分组库存预占ID --------------------------
	private Map<String, String> groupStockPreOccupyByMerchant(
		List<CreateOrderRequest.MerchantSkuGroupDTO> merchantGroups,
		Map<String, ProductFeignClient.SkuDTO> skuMap,
		List<String> preOccupyIds
	) {
		// 先构建“SKU ID → 预占ID”映射（假设库存服务返回顺序与请求顺序一致）
		List<String> skuIdsInRequestOrder = this.flattenSkuList(merchantGroups).stream()
			.map(RiskControlFeignClient.SkuRiskDTO::getSkuId)
			.toList();
		Map<String, String> skuPreOccupyMap = new HashMap<>();
		for (int i = 0; i < skuIdsInRequestOrder.size(); i++) {
			skuPreOccupyMap.put(skuIdsInRequestOrder.get(i), preOccupyIds.get(i));
		}

		// 再按商家分组预占ID（逗号分隔）
		Map<String, String> merchantPreOccupyMap = new HashMap<>();
		for (CreateOrderRequest.MerchantSkuGroupDTO group : merchantGroups) {
			String merchantId = group.getMerchantId();
			List<String> merchantPreOccupyIds = group.getSkuItems().stream()
				.map(item -> skuPreOccupyMap.get(item.getSkuId()))
				.collect(Collectors.toList());
			merchantPreOccupyMap.put(merchantId, String.join(",", merchantPreOccupyIds));
		}
		return merchantPreOccupyMap;
	}

	// -------------------------- 工具方法：计算商家运费（按商家运费政策） --------------------------
	private Money calculateMerchantFreight(
		CreateOrderRequest.MerchantSkuGroupDTO merchantGroup,
		List<SubOrderItem> subItemList,
		Map<String, ProductFeignClient.SkuDTO> skuMap,
		MerchantFeignClient.MerchantDTO merchantDTO
	) {
		// 1. 计算该商家下所有SKU的总重量（用于运费计算）
		double totalWeight = subItemList.stream()
			.map(item -> skuMap.get(item.getSkuId()).getWeight() * item.getQuantity())
			.reduce(0.0, Double::sum);

		// 2. 调用物流服务计算运费（传入商家地址、总重量、商家运费政策）
		LogisticsFeignClient.CalculateFreightResponse freightResponse = logisticsFeignClient.calculateMerchantFreight(
			LogisticsFeignClient.CalculateMerchantFreightRequest.builder()
				.merchantId(merchantGroup.getMerchantId())
				.addressId(merchantGroup.getMerchantAddressId())
				.totalWeight(totalWeight)
				.freeFreightThreshold(merchantDTO.getFreeFreightThreshold()) // 商家满额包邮阈值
				.baseFreight(merchantDTO.getBaseFreight()) // 商家基础运费
				.build()
		);
		return Money.of(freightResponse.getFreightAmount());
	}

	// -------------------------- 工具方法：回滚所有优惠券（平台+商家） --------------------------
	private void rollbackAllCoupons(
		PromotionFeignClient.PreUseCouponResponse platformCouponResp,
		Map<String, PromotionFeignClient.PreUseCouponResponse> merchantCouponMap
	) {
		// 回滚平台优惠券
		if (platformCouponResp != null) {
			promotionFeignClient.rollbackCouponUse(platformCouponResp.getLockId());
			log.info("回滚平台优惠券：couponId={}, lockId={}", platformCouponResp.getCouponId(),
				platformCouponResp.getLockId());
		}
		// 回滚商家优惠券
		for (Map.Entry<String, PromotionFeignClient.PreUseCouponResponse> entry : merchantCouponMap.entrySet()) {
			String merchantId = entry.getKey();
			PromotionFeignClient.PreUseCouponResponse resp = entry.getValue();
			promotionFeignClient.rollbackCouponUse(resp.getLockId());
			log.info("回滚商家[{}]优惠券：couponId={}, lockId={}", merchantId, resp.getCouponId(),
				resp.getLockId());
		}
	}

	// -------------------------- 工具方法：构建子订单优惠券锁ID（平台+商家，逗号分隔） --------------------------
	private String buildSubCouponLockId(
		PromotionFeignClient.PreUseCouponResponse platformResp,
		PromotionFeignClient.PreUseCouponResponse merchantResp
	) {
		List<String> lockIds = new ArrayList<>();
		if (platformResp != null) {
			lockIds.add(platformResp.getLockId());
		}
		if (merchantResp != null) {
			lockIds.add(merchantResp.getLockId());
		}
		return lockIds.isEmpty() ? null : String.join(",", lockIds);
	}

	// -------------------------- 工具方法：构建多店铺订单响应DTO --------------------------
	private CreateOrderResponse buildMultiShopOrderResponse(
		MainOrder mainOrder,
		List<SubOrder> subOrders,
		PaymentFeignClient.CreateMergePayResponse mergePayResponse
	) {
		CreateOrderResponse response = new CreateOrderResponse();
		response.setMainOrderNo(mainOrder.getMainOrderNo());
		response.setMainOrderStatus(mainOrder.getMainStatus());
		response.setTotalPayAmount(mainOrder.getTotalPayAmount());
		response.setMergePayUrl(mergePayResponse.getMergePayUrl());
		response.setPlatformDiscount(mainOrder.getPlatformDiscount());

		// 构建子订单响应列表
		List<CreateOrderResponse.SubOrderResponseDTO> subRespList = subOrders.stream()
			.map(sub -> {
				CreateOrderResponse.SubOrderResponseDTO subResp = new CreateOrderResponse.SubOrderResponseDTO();
				subResp.setSubOrderNo(sub.getOrderNo());
				subResp.setMerchantId(sub.getMerchantId());
				subResp.setMerchantName(sub.getMerchantName());
				subResp.setSubPayAmount(sub.getSubPayAmount());
				subResp.setSubOrderStatus(sub.getStatus().name());
				subResp.setMerchantDiscount(sub.getSubMerchantDiscount());
				subResp.setSubPlatformDiscount(sub.getSubPlatformDiscount());
				subResp.setSubFreight(sub.getSubFreight());

				// 构建子订单SKU明细响应
				List<CreateOrderResponse.SubOrderItemResponseDTO> itemRespList = sub.getSubItems()
					.stream()
					.map(item -> {
						CreateOrderResponse.SubOrderItemResponseDTO itemResp = new CreateOrderResponse.SubOrderItemResponseDTO();
						itemResp.setSkuId(item.getSkuId());
						itemResp.setSkuName(item.getSkuName());
						itemResp.setSkuImage(item.getSkuImage());
						itemResp.setSpecCombination(item.getSpecCombination());
						itemResp.setUnitPrice(item.getUnitPrice());
						itemResp.setQuantity(item.getQuantity());
						itemResp.setItemTotalPrice(item.getItemTotalPrice());
						itemResp.setItemPlatformDiscount(item.getItemPlatformDiscount());
						return itemResp;
					})
					.collect(Collectors.toList());
				subResp.setSubItemList(itemRespList);
				return subResp;
			})
			.collect(Collectors.toList());
		response.setSubOrderList(subRespList);
		return response;
	}

	// -------------------------- 工具方法：扁平化SKU用于优惠券核销（带单价） --------------------------
	private List<PromotionFeignClient.SkuDTO> flattenSkuForCoupon(
		List<CreateOrderRequest.MerchantSkuGroupDTO> merchantGroups,
		Map<String, ProductFeignClient.SkuDTO> skuMap
	) {
		return merchantGroups.stream()
			.flatMap(group -> group.getSkuItems().stream()
				.map(item -> {
					ProductFeignClient.SkuDTO skuDTO = skuMap.get(item.getSkuId());
					return new PromotionFeignClient.SkuDTO(
						item.getSkuId(),
						item.getQuantity(),
						skuDTO.getSalePrice(),
						skuDTO.getMerchantId()
					);
				})
			)
			.collect(Collectors.toList());
	}
}