package com.github.spud.tinystore.order.domain.service.pricing.step;

import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingAdjustment;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingLine;
import com.github.spud.tinystore.order.domain.service.pricing.spi.PricingStep;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiscountAllocationStep implements PricingStep {

	@Override
	public void execute(PricingContext context) {
		List<PricingLine> lines = context.getLines();
		long itemsTotal = context.getItemsTotal();

		long orderLevelDiscount = context.getAdjustments().stream()
			.filter(a -> a.getScope() == PricingAdjustment.Scope.ORDER)
			.mapToLong(PricingAdjustment::getAmountCents)
			.filter(v -> v < 0)
			.sum();

		long totalDiscountAbs = Math.abs(orderLevelDiscount);
		if (totalDiscountAbs == 0L || itemsTotal == 0L) {
			context.setDiscountTotal(0L);
			lines.forEach(l -> l.setNetLineTotalCents(l.getRawLineTotalCents()))
			;
			return;
		}

		long allocatedSum = 0L;
		for (int i = 0; i < lines.size(); i++) {
			PricingLine l = lines.get(i);
			long raw = l.getRawLineTotalCents();
			long alloc;
			if (i < lines.size() - 1) {
				alloc = (long) Math.floor((double) totalDiscountAbs * raw / itemsTotal);
			} else {
				alloc = totalDiscountAbs - allocatedSum; // last line adjust
			}
			if (alloc > raw) {
				alloc = raw; // cap not exceed raw
			}

			l.setDiscountAllocatedCents(alloc);
			l.setNetLineTotalCents(raw - alloc);
			allocatedSum += alloc;
		}

		context.setDiscountTotal(allocatedSum);
	}
}
