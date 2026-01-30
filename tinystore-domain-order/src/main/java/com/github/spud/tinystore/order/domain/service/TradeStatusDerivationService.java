package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Trade 展示态推导服务
 * 
 * 根据 Trade + 子单集合 + 售后集合 + 包裹集合，推导出前端展示用的 TradeViewStatus
 * 约束：展示态不作为写入条件，不参与并发控制（避免总状态爆炸）
 */
@Service
public class TradeStatusDerivationService {

    /**
     * 推导交易展示态
     * 
     * @param trade Trade 聚合根
     * @param shopOrders 关联的店铺子单列表
     * @param afterSaleCases 关联的售后案件列表（可选）
     * @param packages 关联的履约包裹列表（可选）
     * @return TradeViewStatus 展示态（仅用于返回给前端/查询）
     */
    public TradeViewStatus deriveViewStatus(
        TradeEntity trade,
        List<ShopOrderEntity> shopOrders,
        List<AfterSaleCaseEntity> afterSaleCases,
        List<FulfillmentPackageEntity> packages) {

        // 基础状态：优先看支付状态
        if ("UNPAID".equals(trade.getPayStatus())) {
            return TradeViewStatus.WAITING_PAYMENT;
        }

        // 如果已退款
        if ("REFUNDED".equals(trade.getPayStatus())) {
            return TradeViewStatus.REFUNDED;
        }
        if ("PART_REFUNDED".equals(trade.getPayStatus())) {
            return TradeViewStatus.PART_REFUNDED;
        }

        // 已支付，检查子单状态
        if (shopOrders == null || shopOrders.isEmpty()) {
            return TradeViewStatus.PAID;
        }

        // 所有子单都关闭 -> 交易关闭
        boolean allClosed = shopOrders.stream().allMatch(o -> "CLOSED".equals(o.getOrderStatus()));
        if (allClosed) {
            return TradeViewStatus.CLOSED;
        }

        // 所有子单都成功 -> 交易成功
        boolean allSuccess = shopOrders.stream().allMatch(o -> "SUCCESS".equals(o.getOrderStatus()));
        if (allSuccess) {
            return TradeViewStatus.SUCCESS;
        }

        // 有任何子单在待收货 -> 整体待收货
        boolean anyPendingReceive = shopOrders.stream().anyMatch(o -> "PENDING_RECEIVE".equals(o.getOrderStatus()));
        if (anyPendingReceive) {
            return TradeViewStatus.WAITING_RECEIVE;
        }

        // 有任何子单在待发货 -> 整体待发货
        boolean anyPendingShip = shopOrders.stream().anyMatch(o -> "PENDING_SHIP".equals(o.getOrderStatus()));
        if (anyPendingShip) {
            return TradeViewStatus.WAITING_SHIP;
        }

        // 默认：已支付
        return TradeViewStatus.PAID;
    }

    /**
     * 交易展示态枚举（仅用于前端展示，不落库）
     */
    public enum TradeViewStatus {
        /** 待支付 */
        WAITING_PAYMENT,
        /** 已支付 */
        PAID,
        /** 待发货 */
        WAITING_SHIP,
        /** 待收货 */
        WAITING_RECEIVE,
        /** 交易成功 */
        SUCCESS,
        /** 部分退款 */
        PART_REFUNDED,
        /** 已退款 */
        REFUNDED,
        /** 交易关闭 */
        CLOSED
    }
}
