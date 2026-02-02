package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.application.service.PaymentApplicationService;
import com.github.spud.tinystore.order.application.query.TradeQueryService;
import com.github.spud.tinystore.order.interfaces.dto.request.*;
import com.github.spud.tinystore.order.interfaces.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private TradeQueryService tradeQueryService;

    /**
     * POST /order/trades - 创建交易
     */
    @PostMapping
    public ResponseEntity<OrderHttpResponse<CreateTradeData>> createTrade(
        @RequestBody CreateTradeRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            // 兼容性处理：优先使用新字段，若新字段为空则尝试从 couponCode 转换
            java.util.List<String> platformCodes = request.getPlatformCouponCodes();
            java.util.Map<String, java.util.List<String>> shopCodesMap = request.getShopCouponCodesByShop();
            
            if ((platformCodes == null || platformCodes.isEmpty()) && 
                (shopCodesMap == null || shopCodesMap.isEmpty()) &&
                request.getCouponCode() != null && !request.getCouponCode().isEmpty()) {
                // 兼容模式：将 couponCode 视为平台券
                platformCodes = java.util.Collections.singletonList(request.getCouponCode());
            }
            
            // 构建 CreateTradeCommand
            CreateTradeCommand.CreateTradeCommandBuilder commandBuilder = CreateTradeCommand.builder()
                .tradeId(request.getTradeId())
                .buyerId(request.getBuyerId())
                .buyerNick(request.getBuyerNick())
                .addressId(request.getAddressId())
                .couponCode(request.getCouponCode())
                .platformCouponCodes(platformCodes)
                .shopCouponCodesByShop(shopCodesMap)
                .traceId(request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString());

            if (request.getOrderLines() != null) {
                java.util.List<CreateTradeCommand.OrderLineCommand> lineCommands = new java.util.ArrayList<>();
                for (CreateTradeRequest.OrderLineItem line : request.getOrderLines()) {
                    Long weightGrams = line.getWeightGrams() != null ? line.getWeightGrams() : 0L;
                    CreateTradeCommand.OrderLineCommand lineCmd = CreateTradeCommand.OrderLineCommand.builder()
                        .skuId(line.getSkuId())
                        .productId(line.getProductId())
                        .productName(line.getProductName())
                        .shopId(line.getShopId())
                        .sellerId(line.getSellerId())
                        .quantity(line.getQuantity())
                        .priceCents(line.getPriceCents())
                        .weightGrams(weightGrams)
                        .build();
                    lineCommands.add(lineCmd);
                }
                commandBuilder.orderLines(lineCommands);
            }

            CreateTradeCommand command = commandBuilder.build();

            // 调用应用服务
            CreateTradeData result = tradeApplicationService.createTrade(idempotencyKey, command);

            log.info("Trade created: tradeId={}, idempotencyKey={}", result.getTradeId(), idempotencyKey);

            return ResponseEntity.ok(OrderHttpResponse.ok(result));

        } catch (Exception e) {
            log.error("Create trade failed", e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Create trade failed: " + e.getMessage()));
        }
    }

    /**
     * GET /order/trades/{tradeId} - 获取交易详情
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<OrderHttpResponse<TradeDetailData>> getTrade(@PathVariable String tradeId) {
        try {
            TradeDetailData tradeDetail = tradeQueryService.getTradeDetail(tradeId);
            return ResponseEntity.ok(OrderHttpResponse.ok(tradeDetail));

        } catch (Exception e) {
            log.error("Get trade failed: tradeId={}", tradeId, e);
            return ResponseEntity.status(404).body(
                OrderHttpResponse.fail(404, "Trade not found: " + tradeId));
        }
    }

    /**
     * POST /order/trades/{tradeId}/cancel - 取消交易
     */
    @PostMapping("/{tradeId}/cancel")
    public ResponseEntity<OrderHttpResponse<Void>> cancelTrade(
        @PathVariable String tradeId,
        @RequestBody(required = false) CancelTradeRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String reason = (request != null && request.getReason() != null) 
                ? request.getReason() : "User cancel";
            String traceId = (request != null && request.getTraceId() != null)
                ? request.getTraceId() : UUID.randomUUID().toString();

            CancelTradeCommand command = CancelTradeCommand.builder()
                .tradeId(tradeId)
                .reason(reason)
                .traceId(traceId)
                .build();

            tradeApplicationService.cancelTrade(idempotencyKey, command);

            return ResponseEntity.ok(OrderHttpResponse.ok("Trade cancelled"));

        } catch (Exception e) {
            log.error("Cancel trade failed: tradeId={}", tradeId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Cancel trade failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/trades/{tradeId}/pay/callback - 支付成功回调
     */
    @PostMapping("/{tradeId}/pay/callback")
    public ResponseEntity<OrderHttpResponse<Void>> paymentCallback(
        @PathVariable String tradeId,
        @RequestBody PaymentCallbackRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            PaymentSucceededCommand command = PaymentSucceededCommand.builder()
                .paymentId(request.getPaymentIntentId())
                .tradeId(tradeId)
                .paidAmountCents(request.getAmountCents())
                .traceId(request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString())
                .build();

            tradeApplicationService.onPaymentSucceeded(idempotencyKey, command);

            return ResponseEntity.ok(OrderHttpResponse.ok("Payment callback processed"));

        } catch (Exception e) {
            log.error("Payment callback failed: tradeId={}", tradeId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Payment callback failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/trades/{tradeId}/refund/callback - 退款结果通知（支付域回调）
     */
    @PostMapping("/{tradeId}/refund/callback")
    public ResponseEntity<OrderHttpResponse<Void>> refundCallback(
        @PathVariable String tradeId,
        @RequestBody RefundCallbackRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            if (request.getRefundId() == null || request.getRefundStatus() == null) {
                return ResponseEntity.status(400).body(
                    OrderHttpResponse.fail(400, "Missing required fields: refundId or refundStatus"));
            }

            // 仅 SUCCESS 状态才触发业务收尾
            if ("SUCCESS".equals(request.getRefundStatus())) {
                String reason = request.getReason() != null ? request.getReason() : "Refund succeeded";
                String traceId = request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString();
                
                paymentApplicationService.processRefund(
                    idempotencyKey, 
                    tradeId, 
                    request.getRefundId(), 
                    request.getRefundAmountCents(), 
                    reason, 
                    traceId
                );
                
                log.info("Refund callback SUCCESS processed: tradeId={}, refundId={}", 
                    tradeId, request.getRefundId());
            } else {
                // FAIL/PROCESSING 状态记录日志，允许后续补偿
                log.warn("Refund callback non-SUCCESS status: tradeId={}, refundId={}, status={}", 
                    tradeId, request.getRefundId(), request.getRefundStatus());
            }

            return ResponseEntity.ok(OrderHttpResponse.ok("Refund callback received"));

        } catch (Exception e) {
            log.error("Refund callback failed: tradeId={}", tradeId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Refund callback failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/trades/{tradeId}/confirm-receipt - 确认收货
     */
    @PostMapping("/{tradeId}/confirm-receipt")
    public ResponseEntity<OrderHttpResponse<Void>> confirmReceipt(
        @PathVariable String tradeId,
        @RequestBody(required = false) ConfirmReceiptRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = (request != null && request.getTraceId() != null) 
                ? request.getTraceId() : UUID.randomUUID().toString();

            // 确认收货：找到该 trade 下的所有订单并确认收货
            tradeApplicationService.confirmTradeReceipt(tradeId, traceId);

            return ResponseEntity.ok(OrderHttpResponse.ok("Receipt confirmed"));

        } catch (Exception e) {
            log.error("Confirm receipt failed: tradeId={}", tradeId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Confirm receipt failed: " + e.getMessage()));
        }
    }
}
