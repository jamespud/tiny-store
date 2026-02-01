package com.github.spud.tinystore.order.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.application.service.PaymentApplicationService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OrderLineJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.FulfillmentPackageJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.AfterSaleCaseJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PackageOrderRefJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 交易 REST 控制器
 * 路径前缀：/order/trades
 */
@Slf4j
@RestController
@RequestMapping("/order/trades")
public class TradeController {

    @Autowired
    private TradeApplicationService tradeApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private TradeJpaRepository tradeJpaRepository;

    @Autowired
    private ShopOrderJpaRepository shopOrderJpaRepository;

    @Autowired
    private OrderLineJpaRepository orderLineJpaRepository;

    @Autowired
    private FulfillmentPackageJpaRepository fulfillmentPackageJpaRepository;

    @Autowired
    private AfterSaleCaseJpaRepository afterSaleCaseJpaRepository;

    @Autowired
    private PackageOrderRefJpaRepository packageOrderRefJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * POST /order/trades - 创建交易
     * 
     * @param request 创建交易请求
     *   {
     *     "tradeId": "uuid",
     *     "buyerId": "buyer_123",
     *     "buyerNick": "buyer_nick",
     *     "couponCode": "PROMO_2024",
     *     "orderLines": [
     *       {
     *         "skuId": "sku_001",
     *         "productId": "prod_001",
     *         "productName": "iPhone 15",
     *         "shopId": "shop_001",
     *         "sellerId": "seller_001",
     *         "quantity": 1,
     *         "priceCents": 99999
     *       }
     *     ]
     *   }
     * @param idempotencyKey 幂等键（Idempotency-Key 请求头）
     * @return 返回 tradeId, payableAmountCents, paymentIntentId
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTrade(
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {

            // 构建 CreateTradeCommand
            CreateTradeCommand.CreateTradeCommandBuilder commandBuilder = CreateTradeCommand.builder()
                .tradeId((String) request.get("tradeId"))
                .buyerId((String) request.get("buyerId"))
                .buyerNick((String) request.get("buyerNick"))
                .addressId((String) request.get("addressId"))
                .couponCode((String) request.get("couponCode"))
                .traceId((String) request.getOrDefault("traceId", UUID.randomUUID().toString()));

            if (request.get("orderLines") != null) {
                // 解析 orderLines
                java.util.List<Map<String, Object>> lines = 
                    (java.util.List<Map<String, Object>>) request.get("orderLines");
                java.util.List<CreateTradeCommand.OrderLineCommand> lineCommands = new java.util.ArrayList<>();

                for (Map<String, Object> line : lines) {
                    Long weightGrams = line.containsKey("weightGrams") && line.get("weightGrams") != null
                        ? ((Number) line.get("weightGrams")).longValue()
                        : 0L;
                    CreateTradeCommand.OrderLineCommand lineCmd = CreateTradeCommand.OrderLineCommand.builder()
                        .skuId((String) line.get("skuId"))
                        .productId((String) line.get("productId"))
                        .productName((String) line.get("productName"))
                        .shopId((String) line.get("shopId"))
                        .sellerId((String) line.get("sellerId"))
                        .quantity(((Number) line.get("quantity")).intValue())
                        .priceCents(((Number) line.get("priceCents")).longValue())
                        .weightGrams(weightGrams)
                        .build();
                    lineCommands.add(lineCmd);
                }
                commandBuilder.orderLines(lineCommands);
            }

            CreateTradeCommand command = commandBuilder.build();

            // 调用应用服务
            Map<String, Object> result = tradeApplicationService.createTrade(idempotencyKey, command);

            log.info("Trade created: tradeId={}, idempotencyKey={}", result.get("tradeId"), idempotencyKey);

            // 返回成功响应
            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", result);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Create trade failed", e);
            return buildErrorResponse(500, "Create trade failed: " + e.getMessage());
        }
    }

    /**
     * GET /order/trades/{tradeId} - 获取交易详情
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<Map<String, Object>> getTrade(@PathVariable String tradeId) {
        try {
            var trade = tradeJpaRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found: " + tradeId));

            // 查询该交易下的所有店铺订单
            var shopOrders = shopOrderJpaRepository.findByTradeId(tradeId);

            // 构建订单详情列表
            java.util.List<Map<String, Object>> ordersData = new java.util.ArrayList<>();
            for (var shopOrder : shopOrders) {
                // 查询订单行
                var orderLines = orderLineJpaRepository.findByOrderId(shopOrder.getOrderId());
                java.util.List<Map<String, Object>> linesData = orderLines.stream()
                    .map(line -> {
                        Map<String, Object> lineMap = new HashMap<>();
                        lineMap.put("skuId", line.getSkuId());
                        lineMap.put("productId", line.getProductId());
                        lineMap.put("productName", line.getProductName());
                        lineMap.put("quantity", line.getQuantity());
                        lineMap.put("priceCents", line.getPriceCents());
                        lineMap.put("lineAmountCents", line.getLineAmountCents());
                        return lineMap;
                    })
                    .collect(java.util.stream.Collectors.toList());

                // 查询履约包裹（通过关联表）
                var packageRefs = packageOrderRefJpaRepository.findByOrderId(shopOrder.getOrderId());
                java.util.List<Map<String, Object>> packagesData = new java.util.ArrayList<>();
                for (var ref : packageRefs) {
                    fulfillmentPackageJpaRepository.findByPackageId(ref.getPackageId())
                        .ifPresent(pkg -> {
                            Map<String, Object> pkgData = new HashMap<>();
                            pkgData.put("packageId", pkg.getPackageId());
                            pkgData.put("waybillNo", pkg.getLogisticsNo());
                            pkgData.put("logisticsCompany", pkg.getLogisticsCompanyId());
                            pkgData.put("shippedAt", pkg.getShippedAt() != null ? pkg.getShippedAt().toString() : null);
                            pkgData.put("deliveredAt", pkg.getDeliveredAt() != null ? pkg.getDeliveredAt().toString() : null);
                            packagesData.add(pkgData);
                        });
                }

                // 查询售后案件
                var afterSaleCases = afterSaleCaseJpaRepository.findByOrderId(shopOrder.getOrderId());
                java.util.List<Map<String, Object>> casesData = afterSaleCases.stream()
                    .map(c -> {
                        Map<String, Object> caseData = new HashMap<>();
                        caseData.put("caseId", c.getCaseId());
                        caseData.put("caseType", c.getCaseType());
                        caseData.put("caseStatus", c.getCaseStatus());
                        caseData.put("refundId", c.getRefundId());
                        caseData.put("refundAmountCents", c.getRefundAmountCents());
                        caseData.put("createdAt", c.getCreatedAt().toString());
                        return caseData;
                    })
                    .collect(java.util.stream.Collectors.toList());

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderId", shopOrder.getOrderId());
                orderData.put("shopId", shopOrder.getShopId());
                orderData.put("sellerId", shopOrder.getSellerId());
                orderData.put("orderStatus", shopOrder.getOrderStatus());
                orderData.put("createdAt", shopOrder.getCreatedAt().toString());
                orderData.put("orderLines", linesData);
                orderData.put("packages", packagesData);
                orderData.put("afterSaleCases", casesData);

                ordersData.add(orderData);
            }

            Map<String, Object> tradeData = new HashMap<>();
            tradeData.put("tradeId", trade.getTradeId());
            tradeData.put("buyerId", trade.getBuyerId());
            tradeData.put("buyerNick", trade.getBuyerNick());
            tradeData.put("payStatus", trade.getPayStatus());
            tradeData.put("totalAmountCents", trade.getTotalAmountCents());
            tradeData.put("discountAmountCents", trade.getDiscountAmountCents());
            tradeData.put("payableAmountCents", trade.getPayableAmountCents());
            tradeData.put("createdAt", trade.getCreatedAt().toString());
            tradeData.put("shopOrders", ordersData);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", tradeData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get trade failed: tradeId={}", tradeId, e);
            return buildErrorResponse(404, "Trade not found: " + tradeId);
        }
    }

    /**
     * POST /order/trades/{tradeId}/cancel - 取消交易
     */
    @PostMapping("/{tradeId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTrade(
        @PathVariable String tradeId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {

            String reason = request != null ? (String) request.get("reason") : "User cancel";

            CancelTradeCommand command = CancelTradeCommand.builder()
                .tradeId(tradeId)
                .reason(reason)
                .traceId((String) (request != null ? request.get("traceId") : null))
                .build();

            tradeApplicationService.cancelTrade(idempotencyKey, command);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Trade cancelled");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Cancel trade failed: tradeId={}", tradeId, e);
            return buildErrorResponse(500, "Cancel trade failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/trades/{tradeId}/pay/callback - 支付成功回调
     */
    @PostMapping("/{tradeId}/pay/callback")
    public ResponseEntity<Map<String, Object>> paymentCallback(
        @PathVariable String tradeId,
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            // 兼容旧字段 paymentId 与新字段 paymentIntentId
            String paymentId = request.containsKey("paymentIntentId") 
                ? (String) request.get("paymentIntentId")
                : (String) request.get("paymentId");
                
            Long amountCents = ((Number) request.get("amountCents")).longValue();

            PaymentSucceededCommand command = PaymentSucceededCommand.builder()
                .paymentId(paymentId)
                .tradeId(tradeId)
                .paidAmountCents(amountCents)
                .traceId((String) request.getOrDefault("traceId", UUID.randomUUID().toString()))
                .build();

            tradeApplicationService.onPaymentSucceeded(idempotencyKey, command);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Payment callback processed");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Payment callback failed: tradeId={}", tradeId, e);
            return buildErrorResponse(500, "Payment callback failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/trades/{tradeId}/refund/callback - 退款结果通知（支付域回调）
     */
    @PostMapping("/{tradeId}/refund/callback")
    public ResponseEntity<Map<String, Object>> refundCallback(
        @PathVariable String tradeId,
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String refundId = (String) request.get("refundId");
            String refundStatus = (String) request.get("refundStatus");  // SUCCESS/FAIL/PROCESSING
            Long refundAmountCents = ((Number) request.get("refundAmountCents")).longValue();
            
            if (refundId == null || refundStatus == null) {
                return buildErrorResponse(400, "Missing required fields: refundId or refundStatus");
            }

            // 仅 SUCCESS 状态才触发业务收尾
            if ("SUCCESS".equals(refundStatus)) {
                String reason = (String) request.getOrDefault("reason", "Refund succeeded");
                paymentApplicationService.processRefund(
                    idempotencyKey, 
                    tradeId, 
                    refundId, 
                    refundAmountCents, 
                    reason, 
                    (String) request.getOrDefault("traceId", UUID.randomUUID().toString())
                );
                
                log.info("Refund callback SUCCESS processed: tradeId={}, refundId={}", tradeId, refundId);
            } else {
                // FAIL/PROCESSING 状态记录日志，允许后续补偿
                log.warn("Refund callback non-SUCCESS status: tradeId={}, refundId={}, status={}", 
                    tradeId, refundId, refundStatus);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Refund callback received");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Refund callback failed: tradeId={}", tradeId, e);
            return buildErrorResponse(500, "Refund callback failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/trades/{tradeId}/confirm-receipt - 确认收货
     */
    @PostMapping("/{tradeId}/confirm-receipt")
    public ResponseEntity<Map<String, Object>> confirmReceipt(
        @PathVariable String tradeId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = request != null ? (String) request.get("traceId") : UUID.randomUUID().toString();

            // 确认收货：找到该 trade 下的所有订单并确认收货
            tradeApplicationService.confirmTradeReceipt(tradeId, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Receipt confirmed");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Confirm receipt failed: tradeId={}", tradeId, e);
            return buildErrorResponse(500, "Confirm receipt failed: " + e.getMessage());
        }
    }

    // ============ 辅助方法 ============

    private ResponseEntity<Map<String, Object>> buildErrorResponse(int code, String msg) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("msg", msg);
        return ResponseEntity.status(code).body(response);
    }
}
