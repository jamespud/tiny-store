package com.github.spud.tinystore.order.interfaces.dto.response;

import com.github.spud.tinystore.order.domain.enums.MainOrderStatus;
import com.github.spud.tinystore.order.domain.model.Money;
import java.util.List;
import lombok.Data;

/**
 * 多店铺创建订单响应DTO（返回主订单+子订单信息）
 */
@Data
public class CreateOrderResponse {

	private String mainOrderNo;

	private MainOrderStatus mainOrderStatus;

	private Money totalPayAmount;

	private Integer payExpireSeconds = 1800; // 默认30分钟

	private String mergePayUrl;

	private Money platformDiscount;

	private List<SubOrderResponseDTO> subOrderList;

	// -------------------------- 子订单响应DTO --------------------------
	@Data
	public static class SubOrderResponseDTO {

		private String subOrderNo;

		private String merchantId;

		private String merchantName;

		private Money subPayAmount;

		private String subOrderStatus; // 如“SUB_PENDING_PAY”

		private Money merchantDiscount;

		private Money subPlatformDiscount;

		private Money subFreight;

		private List<SubOrderItemResponseDTO> subItemList;
	}

	// -------------------------- 子订单SKU明细响应DTO --------------------------
	@Data
	public static class SubOrderItemResponseDTO {

		private String skuId;
		private String skuName;
		private String skuImage;
		private String specCombination;
		private Money unitPrice;
		private Integer quantity;
		private Money itemTotalPrice;
		private Money itemPlatformDiscount; // 该SKU分摊的平台优惠
	}
}