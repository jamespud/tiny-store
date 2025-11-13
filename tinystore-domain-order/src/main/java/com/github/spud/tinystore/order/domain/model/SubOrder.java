package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
import com.github.spud.tinystore.order.domain.statemachine.status.OrderStatus;
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

	private final String id;                      // 子订单数据库主键
	private final OrderNo orderNo;                // 子订单号（业务唯一，如“DO_S_20240520xxxx”）
	private final OrderNo mainOrderNo;            // 关联主订单号（外键）
	private final String merchantId;              // 商家ID（子订单核心关联，1个子订单1个商家）
	private final String merchantName;            // 商家名称（冗余，避免后续查询商家服务）
	@Setter
	private OrderStatus status;                   // 子订单状态（SUB_PENDING_PAY：待支付；SUB_PAID：已支付；SUB_SHIPPED：已发货...）
	private List<Discount> discounts;             // 子订单优惠信息列表（如商家优惠券等，仅该商家可用）
	private final List<SubOrderItem> subItems;    // 子订单SKU明细（该商家下的SKU）

	private final String stockPreOccupyIds;       // 该子订单关联的库存预占ID（逗号分隔，支付后扣减）
	private final String couponLockId;            // 该子订单关联的优惠券锁ID（商家优惠券+平台优惠分摊对应的锁）

	private final PricingSummary pricingSummary;  // 子订单价格汇总信息
}
