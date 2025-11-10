package com.github.spud.tinystore.order.application.command.user;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Getter
public class SubmitOrderCommand extends ConfirmOrderCommand {

	private final String idempotentKey;
	
	public SubmitOrderCommand(String userId,
													   List<MerchantSkuDTO> merchantSkus, 
													   String platformCouponId, 
													   String addressId,
													   String idempotentKey) {
		super(userId, merchantSkus, platformCouponId, addressId);
		this.idempotentKey = idempotentKey;
	}
}
