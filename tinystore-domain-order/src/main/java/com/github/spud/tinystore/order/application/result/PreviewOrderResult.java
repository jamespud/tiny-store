package com.github.spud.tinystore.order.application.result;

import com.github.spud.tinystore.order.domain.model.*;
import com.github.spud.tinystore.order.interfaces.dto.response.PreviewOrderVO;
import lombok.Data;

import java.util.List;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class PreviewOrderResult {

    private List<ShopProductSnapshot> lines;

    private OrderSummary summary;

    // ttl in seconds
    private long expireAt;

    private PreviewOrderResult() {
    }

    public PreviewOrderResult(List<ShopProductSnapshot> lines, OrderSummary summary, long expireAt) {
        this.lines = lines;
        this.summary = summary;
        this.expireAt = expireAt;
    }

    public static PreviewOrderResult fromOrder(Order order) {
        List<ShopProductSnapshot> snapshots = order.getSubOrders().stream()
                .map(ShopProductSnapshot::fromSuborder)
                .toList();
        PricingSummary pricingSummary = order.getPricingSummary();
        List<DiscountSnapshot> discounts = order.getDiscounts().stream()
                .map(DiscountSnapshot::fromDiscount)
                .toList();
        List<CouponSnapshot> coupons = order.getCoupons().stream()
                .map(CouponSnapshot::fromCoupon)
                .toList();
        List<ChargeItemSnapshot> charges = order.getSubOrders().stream()
                .flatMap(subOrder -> subOrder.charges().stream())
                .map(ChargeItemSnapshot::fromChargeItem)
                .toList();
        OrderSummary summary = new OrderSummary(pricingSummary.total(), pricingSummary.payable(), charges, discounts, coupons);
        return new PreviewOrderResult(snapshots, summary, 5 * 60);
    }

    public PreviewOrderVO toVO() {
        return new PreviewOrderVO(lines, summary, expireAt);
    }

    public record ShopProductSnapshot(String shopId, List<ProductSnapshot> products,
                                      Money total) {

        public static ShopProductSnapshot fromSuborder(SubOrder subOrder) {
            String shopId = subOrder.shopId();
            Money total = subOrder.total();
            List<ProductSnapshot> productSnapshots = subOrder.lines().stream()
                    .map(line -> new ProductSnapshot(line.getSkuId(), line.getUnitPrice(), line.getLinePayable(), line.getQuantity())
                    )
                    .toList();
            return new ShopProductSnapshot(shopId, productSnapshots, total);
        }

    }

    /**
     *
     * @param skuId
     * @param unitPrice 单价
     * @param payable   应付金额
     * @param quantity  数量
     */
    public record ProductSnapshot(String skuId, Money unitPrice, Money payable,
                                  int quantity) {

    }

    public record OrderSummary(Money total, Money payable,
                               List<ChargeItemSnapshot> charges,
                               List<DiscountSnapshot> discounts,
                               List<CouponSnapshot> coupons) {

    }

    public record CouponSnapshot(String couponId, String description,
                                 Money amount) {
        public static CouponSnapshot fromCoupon(Coupon coupon) {
            return new CouponSnapshot(coupon.couponId(), coupon.description(), coupon.amount());
        }
    }

    public record DiscountSnapshot(String description, Money amount) {
        public static DiscountSnapshot fromDiscount(Discount discount) {
            return new DiscountSnapshot(discount.description(), discount.amount());
        }
    }

    public record ChargeItemSnapshot(String type, String description, Money amount) {
        public static ChargeItemSnapshot fromChargeItem(ChargeItem chargeItem) {
            return new ChargeItemSnapshot(chargeItem.type().toString(), chargeItem.description(), chargeItem.amount());
        }
    }

}
