package com.github.spud.tinystore.order.application.command.user;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Builder
@Getter
public class ConfirmOrderCommand {

	String userId;

	private List<MerchantSkuDTO> merchantSkus;

	private String platformCouponId;

	private String addressId;
	
	public ConfirmOrderCommand(String userId,
													   List<MerchantSkuDTO> merchantSkus, 
													   String platformCouponId, 
													   String addressId) {
		this.userId = userId;
		this.merchantSkus = merchantSkus;
		this.platformCouponId = platformCouponId;
		this.addressId = addressId;
	}

	public Set<String> getSkuIds() {
		return this.getMerchantSkus().stream()
			.flatMap(m -> m.skuItems().stream())
			.map(SkuItemDTO::skuId)
			.collect(Collectors.toSet());
	}

	public record MerchantSkuDTO(String merchantId, List<SkuItemDTO> skuItems,
															 String merchantCouponId) {
		
	}

	public record SkuItemDTO(String skuId, Integer quantity) {

	}
}
