package com.github.spud.tinystore.order.domain.model;

import java.util.Map;

/**
 * 订单行
 *
 * @param products 商品及数量
 * @param total    订单行原始总金额
 * @param discount 优惠分摊金额
 * @param payable  应付金额
 */
public record OrderLine(Map<Product, Integer> products, Money total, Money discount,
                        Money payable) {

}