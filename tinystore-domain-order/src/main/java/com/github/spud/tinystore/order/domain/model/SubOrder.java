package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
import com.github.spud.tinystore.order.domain.status.OrderStatus;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 多店铺下单-子订单（对应单个商家，独立处理发货、分账）
 */
@Getter
@Builder
public class SubOrder {

	private final String id;                    // 子订单数据库主键
	private final OrderNo orderNo;              // 子订单号（业务唯一，如“DO_S_20240520xxxx”）
	private final OrderNo mainOrderNo;            // 关联主订单号（外键）
	private final String merchantId;            // 商家ID（子订单核心关联，1个子订单1个商家）
	private final String merchantName;          // 商家名称（冗余，避免后续查询商家服务）
	@Setter
	private OrderStatus status;            			// 子订单状态（SUB_PENDING_PAY：待支付；SUB_PAID：已支付；SUB_SHIPPED：已发货...）
	private final Money subGoodsTotal;          // 子订单商品总价（该商家下所有SKU小计之和）
	private final Money subMerchantDiscount;    // 商家优惠券优惠金额（仅该商家可用）
	private final Money subPlatformDiscount;    // 平台优惠券分摊金额（从主订单总平台优惠拆分）
	private final Money subFreight;              // 子订单运费（商家独立计算，如满额包邮）
	@Setter
	private Money subPayAmount;                  // 子订单实付金额（商品总价-商家优惠-平台分摊优惠+运费）
	private final List<SubOrderItem> subItems;  // 子订单SKU明细（该商家下的SKU）
	private final String merchantCouponId;      // 商家优惠券ID（可选，仅该商家可用）
	private final String stockPreOccupyIds;      // 该子订单关联的库存预占ID（逗号分隔，支付后扣减）
	private final String couponLockId;          // 该子订单关联的优惠券锁ID（商家优惠券+平台优惠分摊对应的锁）
}
