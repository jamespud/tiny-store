package com.github.spud.tinystore.order.application.command.user;

import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand.MerchantSkuDTO.SkuItemDTO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Builder
@Getter
public class PreviewOrderCommand {

	String userId;

	private List<MerchantSkuDTO> merchantSkus;

	private String platformCouponId;

	private String addressId;

	public Set<String> getSkuIds() {
		return this.getMerchantSkus().stream()
			.flatMap(m -> m.skuItems().stream())
			.map(SkuItemDTO::skuId)
			.collect(Collectors.toSet());
	}

	public record MerchantSkuDTO(String merchantId, List<SkuItemDTO> skuItems, String merchantCouponId) {

		public record SkuItemDTO(String skuId, Integer quantity) {

		}
	}
}
