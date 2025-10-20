package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.enums.MainOrderStatus;
import com.github.spud.tinystore.order.domain.model.vo.OrderNo;
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

	private final String id;                 				// 主订单数据库主键
	private final OrderNo orderNo;        					// 主订单号（业务唯一，如“DO_M_20240520xxxx”）
	private final String userId;             				// 用户ID
	@Setter			
	private MainOrderStatus mainStatus;      				// 主订单状态（MERGE_PENDING_PAY：合并待支付；MERGE_PAID：合并已支付；MERGE_CANCELED：合并已取消）
	private final List<SubOrder> subOrders;  				// 子订单列表（1个商家1个，N:1关联主订单）
	// 优惠信息			
	private final List<Discount> discounts;   			// 主订单优惠信息列表（如平台优惠券等）
	private final PricingSummary pricingSummary; 		// 主订单价格汇总信息	
	
	private final Long payExpireTime;        				// 合并支付有效期（30分钟）
}