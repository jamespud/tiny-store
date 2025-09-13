package com.github.spud.tinystore.order.application.service.pricing.spi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PricingAdjustment {

	public enum Type {PROMOTION, COUPON, SHIPPING, TAX, ROUNDING}

	public enum Scope {ORDER, LINE}

	private Type type;
	private Scope scope;
	private String lineSkuId; // when scope=LINE
	private long amountCents; // negative for amount
	private String source;    // rule id or description
}
