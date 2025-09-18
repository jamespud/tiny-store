package com.github.spud.tinystore.order.application.service.pricing.spi;

import com.github.spud.tinystore.order.domain.model.Product;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;

@Data
public class PricingContext {

	private String userId;
	private Collection<Item> items;
	private Map<String, Product> productMap; // keyed by productId (spuId)

	private List<PricingLine> lines = new ArrayList<>();
	private List<PricingAdjustment> adjustments = new ArrayList<>();

	// totals in cents
	private long itemsTotal;
	private long discountTotal;
	private long shippingFee;
	private long taxTotal;
	private long payable;
	private long grandTotal;

	// helper
	public void addAdjustment(PricingAdjustment adj) {
		if (adj != null) {
			adjustments.add(adj);
		}
	}
}
