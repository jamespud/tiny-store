package com.github.spud.tinystore.order.application.service.pricing;

import com.github.spud.tinystore.order.application.service.pricing.spi.PricingContext;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingStep;
import com.github.spud.tinystore.order.application.service.pricing.spi.PricingSummaryView;
import com.github.spud.tinystore.order.application.service.pricing.step.CouponApplyStep;
import com.github.spud.tinystore.order.application.service.pricing.step.DiscountAllocationStep;
import com.github.spud.tinystore.order.application.service.pricing.step.PriceLoadingStep;
import com.github.spud.tinystore.order.application.service.pricing.step.ShippingFeeStep;
import com.github.spud.tinystore.order.application.service.pricing.step.SummaryStep;
import com.github.spud.tinystore.order.application.service.pricing.step.TaxStep;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class PricingPipelineAppService {

    private final List<PricingStep> steps;

    public PricingPipelineAppService(PriceLoadingStep priceLoadingStep,
                                     CouponApplyStep couponApplyStep,
                                     ShippingFeeStep shippingFeeStep, TaxStep taxStep, DiscountAllocationStep discountAllocationStep,
                                     SummaryStep summaryStep) {
        this.steps = List.of(
                priceLoadingStep,
                couponApplyStep,
                shippingFeeStep,
                taxStep,
                discountAllocationStep,
                summaryStep
        );
    }

    public PricingSummaryView price(PricingContext ctx) {
//		priceLoadingStep.execute(ctx);
//		couponApplyStep.execute(ctx);
//		shippingFeeStep.execute(ctx);
//		taxStep.execute(ctx);
//		discountAllocationStep.execute(ctx);
//		summaryStep.execute(ctx);
        for (PricingStep step : steps) {
            step.execute(ctx);
        }

        return PricingSummaryView.builder()
                .itemsTotal(ctx.getItemsTotal())
                .discountTotal(ctx.getDiscountTotal())
                .shippingFee(ctx.getShippingFee())
                .taxTotal(ctx.getTaxTotal())
                .payable(ctx.getPayable())
                .grandTotal(ctx.getGrandTotal())
                .pricingVersion(null)
                .adjustments(ctx.getAdjustments())
                .build();
    }
}
