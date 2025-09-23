package com.github.spud.tinystore.order.application.service.pricing.spi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PricingLine {

    private String shopId;
    private String skuId;
    private int quantity;
    // cents
    private long unitPriceCents;
    private long rawLineTotalCents;
    private long discountAllocatedCents;
    private long netLineTotalCents;
}
