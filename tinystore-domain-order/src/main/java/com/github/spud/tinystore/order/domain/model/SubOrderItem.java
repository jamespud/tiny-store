package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.line.SkuSnapshot;
import lombok.Builder;
import lombok.Getter;

/**
 * 子订单SKU明细（对应单个商家的单个SKU）
 */
@Getter
@Builder
public class SubOrderItem {

	private SkuSnapshot skuSnapshot;
	private final String id;                 // 明细主键
	private final String subOrderNo;         // 关联子订单号
	private final String skuId;              // SKU ID
	private final String skuName;            // SKU名称
	private final String skuImage;           // SKU主图
	private final String specCombination;    // 规格组合（如“颜色=红色;尺码=XL”）
	private final Money unitPrice;           // 购买时单价（商家定价）
	private final Integer quantity;          // 购买数量
	private final Money itemTotalPrice;      // 明细小计金额（单价×数量）
	private final Money itemPlatformDiscount;// 该明细分摊的平台优惠金额（按小计占比拆分）
}