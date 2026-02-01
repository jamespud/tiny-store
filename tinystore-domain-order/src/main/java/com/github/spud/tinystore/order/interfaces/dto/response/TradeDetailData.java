package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易详情响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeDetailData {
    
    private String tradeId;
    private String buyerId;
    private String buyerNick;
    private String payStatus;
    private Long totalAmountCents;
    private Long discountAmountCents;
    private Long payableAmountCents;
    private String createdAt;
    private List<ShopOrderDetail> shopOrders;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopOrderDetail {
        private String orderId;
        private String shopId;
        private String sellerId;
        private String orderStatus;
        private String createdAt;
        private List<OrderLineDetail> orderLines;
        private List<PackageDetail> packages;
        private List<AfterSaleCaseDetail> afterSaleCases;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderLineDetail {
        private String skuId;
        private String productId;
        private String productName;
        private Integer quantity;
        private Long priceCents;
        private Long lineAmountCents;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageDetail {
        private String packageId;
        private String waybillNo;
        private String logisticsCompany;
        private String shippedAt;
        private String deliveredAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AfterSaleCaseDetail {
        private String caseId;
        private String caseType;
        private String caseStatus;
        private String refundId;
        private Long refundAmountCents;
        private String createdAt;
    }
}
