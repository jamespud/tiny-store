package com.github.spud.tinystore.order.interfaces.dto;

import com.github.spud.tinystore.order.application.result.PreviewOrderResult.CouponSnapshot;
import com.github.spud.tinystore.order.domain.model.Money;
import java.util.List;

/**
 * @param total     总价
 * @param shipping  运费
 * @param discounts 优惠列表
 * @param payable   应付总额
 * @author Spud
 * @date 2025/9/4
 */
public record OrderSummary(Money total, Money shipping, List<DiscountSnapshot> discounts,
                           List<CouponSnapshot> coupons,
                           Money payable) {

}