package com.github.spud.tinystore.order.domain.service.pricing.spi;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PricingSummaryView {

	private long itemsTotal;
	private long discountTotal;
	private long shippingFee;
	private long taxTotal;
	private long payable;
	private long grandTotal;
	private String pricingVersion;
	private List<PricingAdjustment> adjustments;
}
