package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.MainOrderStatus;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 多店铺下单-主订单（关联多个子订单，对应用户一次合并支付）
 */
@Getter
@Builder
public class MainOrder {
	private final String id;                 // 主订单数据库主键
	private final String mainOrderNo;        // 主订单号（业务唯一，如“DO_M_20240520xxxx”）
	private final String tenantId;           // 租户ID
	private final String userId;             // 用户ID
	@Setter
	private MainOrderStatus mainStatus;      // 主订单状态（MERGE_PENDING_PAY：合并待支付；MERGE_PAID：合并已支付；MERGE_CANCELED：合并已取消）
	private final Money totalPayAmount;      // 主订单总实付金额（所有子订单实付金额之和）
//	private final Integer payType;           // 支付方式（统一支付方式，不支持子订单不同支付）
	private final Long payExpireTime;        // 合并支付有效期（30分钟）
	private final List<SubOrder> subOrders;  // 子订单列表（1个商家1个，N:1关联主订单）
	private final String platformCouponId;   // 平台优惠券ID（可选，跨店可用）
	private final Money platformDiscount;    // 平台优惠券总优惠金额（需分摊到子订单）
	private final String idempotencyKey;     // 幂等键（关联主订单，避免重复创建）
}