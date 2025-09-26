package com.github.spud.tinystore.order.domain.service.pricing.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;

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
