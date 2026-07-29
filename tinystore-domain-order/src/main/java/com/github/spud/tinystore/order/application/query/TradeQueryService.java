package com.github.spud.tinystore.order.application.query;

import com.github.spud.tinystore.order.domain.model.AfterSaleCase;
import com.github.spud.tinystore.order.domain.model.FulfillmentPackage;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.AfterSaleCaseRepository;
import com.github.spud.tinystore.order.domain.repository.FulfillmentPackageRepository;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.TradeDetailData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交易查询服务（读模型）
 * 负责查询聚合并组装为 Response DTO
 */
@Slf4j
@Service
public class TradeQueryService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ShopOrderRepository shopOrderRepository;

    @Autowired
    private FulfillmentPackageRepository fulfillmentPackageRepository;

    @Autowired
    private AfterSaleCaseRepository afterSaleCaseRepository;

    /**
     * 查询交易详情（含所有子单、订单行、包裹、售后案件）
     * 
     * @param tradeId 交易ID
     * @return 交易详情 DTO
     */
    public TradeDetailData getTradeDetail(String tradeId) {
        // 查询 Trade 聚合根
        Trade trade = tradeRepository.findByTradeId(tradeId)
            .orElseThrow(() -> new RuntimeException("Trade not found: " + tradeId));

        // 查询该交易下的所有店铺订单
        List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(tradeId);

        // 组装店铺订单详情
        List<TradeDetailData.ShopOrderDetail> shopOrderDetails = new ArrayList<>();
        for (ShopOrder shopOrder : shopOrders) {
            // 订单行（已在 ShopOrder 聚合中）
            List<TradeDetailData.OrderLineDetail> orderLineDetails = shopOrder.getOrderLines().stream()
                .map(this::toOrderLineDetail)
                .collect(Collectors.toList());

            // 查询该订单的包裹
            List<FulfillmentPackage> packages = fulfillmentPackageRepository.findByOrderId(shopOrder.getOrderId());
            List<TradeDetailData.PackageDetail> packageDetails = packages.stream()
                .map(this::toPackageDetail)
                .collect(Collectors.toList());

            // 查询该订单的售后案件
            List<AfterSaleCase> afterSaleCases = afterSaleCaseRepository.findByOrderId(shopOrder.getOrderId());
            List<TradeDetailData.AfterSaleCaseDetail> caseDetails = afterSaleCases.stream()
                .map(this::toCaseDetail)
                .collect(Collectors.toList());

            shopOrderDetails.add(TradeDetailData.ShopOrderDetail.builder()
                .orderId(shopOrder.getOrderId())
                .shopId(shopOrder.getShopId())
                .sellerId(shopOrder.getSellerId())
                .orderStatus(shopOrder.getOrderStatus().name())
                .inventoryStatus(shopOrder.getInventoryStatus())
                .createdAt(shopOrder.getCreatedAt().toString())
                .orderLines(orderLineDetails)
                .packages(packageDetails)
                .afterSaleCases(caseDetails)
                .build());
        }

        // 组装 Trade 详情
        return TradeDetailData.builder()
            .tradeId(trade.getTradeId())
            .buyerId(trade.getBuyerId())
            .buyerNick(trade.getBuyerNick())
            .payStatus(trade.getPayStatus().name())
            .totalAmountCents(trade.getTotalAmountCents())
            .discountAmountCents(trade.getDiscountAmountCents())
            .payableAmountCents(trade.getPayableAmountCents())
            .createdAt(trade.getCreatedAt().toString())
            .shopOrders(shopOrderDetails)
            .build();
    }

    private TradeDetailData.OrderLineDetail toOrderLineDetail(OrderLine line) {
        return TradeDetailData.OrderLineDetail.builder()
            .skuId(line.getSkuId())
            .productId(line.getProductId())
            .productName(line.getProductName())
            .quantity(line.getQuantity())
            .priceCents(line.getPriceCents())
            .lineAmountCents(line.getLineAmountCents())
            .build();
    }

    private TradeDetailData.PackageDetail toPackageDetail(FulfillmentPackage pkg) {
        return TradeDetailData.PackageDetail.builder()
            .packageId(pkg.getPackageId())
            .waybillNo(pkg.getWaybillNo())
            .logisticsCompany(pkg.getLogisticsCompany())
            .shippedAt(pkg.getShippedAt() != null ? pkg.getShippedAt().toString() : null)
            .deliveredAt(pkg.getDeliveredAt() != null ? pkg.getDeliveredAt().toString() : null)
            .build();
    }

    private TradeDetailData.AfterSaleCaseDetail toCaseDetail(AfterSaleCase c) {
        return TradeDetailData.AfterSaleCaseDetail.builder()
            .caseId(c.getCaseId())
            .caseType(c.getCaseType().name())
            .caseStatus(c.getCaseStatus().name())
            .refundId(c.getRefundId())
            .refundAmountCents(c.getRefundAmountCents())
            .createdAt(c.getCreatedAt().toString())
            .build();
    }
}
