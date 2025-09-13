package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.event.OrderStatus;
import com.github.spud.tinystore.order.domain.status.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;
import com.github.spud.tinystore.order.domain.status.PaymentStatus;
import java.util.List;
import java.util.Map;

/**
 * 子订单
 *
 * @param orderId  子订单ID
 * @param userId   用户ID
 * @param shopId   店铺ID
 * @param products 商品及数量
 * @param total    订单行原始总金额
 * @param discount 优惠分摊金额
 * @param payable  应付金额
 */
public record OrderLine(String orderId, String userId, String shopId,
                        Map<Product, Integer> products, Money total, Money discount,
                        Money payable, List<ChargeItem> chargeItems, OrderStatus orderStatus,
                        PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                        AfterSaleStatus afterSaleStatus, Address address) {

}