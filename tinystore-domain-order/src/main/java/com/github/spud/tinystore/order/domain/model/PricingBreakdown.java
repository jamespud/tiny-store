package com.github.spud.tinystore.order.domain.model;

/**
 * 价格明细
 *
 * @param itemsTotal    商品总价
 * @param discountTotal 优惠总价
 * @param shippingFee   运费
 * @param taxTotal      税费
 * @param grandTotal    总计
 */
public record PricingBreakdown(Money itemsTotal, Money discountTotal, Money shippingFee,
                               Money taxTotal,
                               Money grandTotal) {

}


