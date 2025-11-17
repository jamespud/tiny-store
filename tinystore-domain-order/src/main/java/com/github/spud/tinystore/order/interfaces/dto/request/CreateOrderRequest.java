package com.github.spud.tinystore.order.interfaces.dto.request;

import com.github.spud.tinystore.order.application.command.user.SubmitOrderCommand;
import com.github.spud.tinystore.order.application.command.user.ConfirmOrderCommand;
import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.stream.Collectors;
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

	public SubmitOrderCommand toCommand(String currentUserId) {
		List<ConfirmOrderCommand.MerchantSkuDTO> merchantSkuDTOs = this.merchantSkuGroups.stream()
			.map(group -> new ConfirmOrderCommand.MerchantSkuDTO(
				group.getMerchantId(),
				group.getSkuItems().stream()
					.map(item -> new ConfirmOrderCommand.SkuItemDTO(item.getSkuId(), item.getQuantity()))
					.collect(Collectors.toList()),
				group.getMerchantCouponId()
			))
			.collect(Collectors.toList());

		String idempotentKey = IdempotencyHelper.getCurrentIdempotencyKey();
		return new SubmitOrderCommand(
			currentUserId,
			merchantSkuDTOs,
			this.platformCouponId,
			this.addressId,
			idempotentKey
		);
	}

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