package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.line.LineItem;
import com.github.spud.tinystore.order.domain.status.FulfillmentStatus;

import java.util.List;

/**
 * 拆分后的子订单
 * <p>
 * 作为持久化的业务载体，承载店铺维度的履约、费用与地址信息
 * 订单行（LineItem）为权威数据来源
 *
 * @param subOrderId          子订单ID
 * @param shopId              店铺ID
 * @param lines               订单行列表（权威数据来源）
 * @param charges             额外费用（如运费、税费等）
 * @param discountAllocations 优惠分摊
 * @param couponAllocations   优惠券分摊
 * @param total               小计
 * @param payable             应付总额
 * @param fulfillmentStatus   履约状态
 * @param address             收货地址（权威归属）
 * @author Spud
 * @date 2025/9/6
 */
public record SubOrder(String subOrderId, String shopId, List<LineItem> lines, List<ChargeItem> charges,
                       List<DiscountAllocation> discountAllocations, List<CouponAllocation> couponAllocations,
                       Money total, Money payable, FulfillmentStatus fulfillmentStatus, Address address) {

    /**
     * 重新计算子订单金额
     *
     * @return 重算后的应付金额
     */
    public Money recompute() {
        // 计算行总额
        Money linesTotal = lines.stream()
                .map(LineItem::getLinePayable)
                .reduce(Money.zero(), Money::add);

        // 加上额外费用
        Money chargesTotal = charges.stream()
                .map(ChargeItem::amount)
                .reduce(Money.zero(), Money::add);

        // 减去子订单级折扣
        Money discountsTotal = discountAllocations.stream()
                .map(DiscountAllocation::amount)
                .reduce(Money.zero(), Money::add);

        Money couponsTotal = couponAllocations.stream()
                .map(CouponAllocation::amount)
                .reduce(Money.zero(), Money::add);

        Money computed = linesTotal.add(chargesTotal).subtract(discountsTotal).subtract(couponsTotal);

        if (!computed.nonNegative()) {
            throw new IllegalStateException("SubOrder payable cannot be negative: " + computed);
        }

        return computed;
    }

    /**
     * 计算小计（不含费用和折扣）
     *
     * @return 小计金额
     */
    public Money computeSubtotal() {
        return lines.stream()
                .map(LineItem::getLineTotal)
                .reduce(Money.zero(), com.github.spud.tinystore.order.domain.model.Money::add);
    }

    /**
     * 应用折扣分摊到各行
     */
    public void applyDiscountAllocations() {
        // TODO: 实现折扣分摊逻辑
        // 按行金额比例分摊或其他策略
    }

    /**
     * 标记部分行已发货
     *
     * @param partialLineIds 部分发货的行ID列表
     */
    public void markShipped(List<String> partialLineIds) {
        // TODO: 实现部分发货逻辑
        // 更新履约状态，可能需要与库存域协调
    }

    /**
     * 标记已签收
     */
    public void markDelivered() {
        // TODO: 实现签收逻辑
        // 更新履约状态为已完成
    }

    /**
     * 检查是否已完全履约
     *
     * @return true 如果所有行都已履约完成
     */
    public boolean isFulfilled() {
        // TODO: 基于库存域的投影/事件判断履约完成状态
        return fulfillmentStatus == FulfillmentStatus.DELIVERED;
    }
}