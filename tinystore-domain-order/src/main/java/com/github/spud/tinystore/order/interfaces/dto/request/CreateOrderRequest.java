package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Data;

/**
 * 多店铺创建订单请求DTO（支持多个商家的SKU）
 */
@Data
public class CreateOrderRequest {

	@NotNull(message = "用户ID不能为空")
	private String userId;

	@NotEmpty(message = "商家-SKU分组列表不能为空")
	@Valid
	private List<MerchantSkuGroupDTO> merchantSkuGroups;

	// 平台优惠券ID（可选）
	private String platformCouponId;
	
	@NotNull(message = "收货地址ID不能为空")
	private String addressId;

	// -------------------------- 商家-SKU分组DTO（1个商家对应1个） --------------------------
	@Data
	@Valid
	public static class MerchantSkuGroupDTO {

		@NotNull(message = "商家ID不能为空")
		private String merchantId;

		@NotEmpty(message = "商家SKU列表不能为空")
		@Valid
		private List<SkuItemDTO> skuItems;

		@NotNull(message = "商家收货地址ID不能为空")
		private String merchantAddressId;

		private String merchantCouponId;
	}

	// -------------------------- SKU明细DTO --------------------------
	@Data
	@Valid
	public static class SkuItemDTO {

		@NotNull(message = "SKU ID不能为空")
		private String skuId;

		@Positive(message = "购买数量必须大于0")
		private Integer quantity;
	}
}