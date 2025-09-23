package com.github.spud.tinystore.order.domain.model;

import java.util.Map;

/**
 * 订单行（已废弃）
 * 
 * @deprecated 此类已被 {@link com.github.spud.tinystore.order.domain.model.line.LineItem} 替代
 * LineItem 作为权威数据来源，提供更清晰的语义与更强的类型安全
 * 此类仅用于旧数据映射和过渡期兼容，不应在新代码中使用
 *
 * @param products 商品及数量
 * @param total    订单行原始总金额
 * @param discount 优惠分摊金额
 * @param payable  应付金额
 */
@Deprecated(since = "2025-09-22", forRemoval = true)
public record OrderLine(Map<Product, Integer> products, Money total, Money discount,
                        Money payable) {

}