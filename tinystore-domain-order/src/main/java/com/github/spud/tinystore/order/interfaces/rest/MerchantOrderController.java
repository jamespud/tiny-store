package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.MerchantFulfillmentService;
import com.github.spud.tinystore.order.application.query.MerchantOrderQueryService;
import com.github.spud.tinystore.order.interfaces.dto.request.*;
import com.github.spud.tinystore.order.interfaces.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 商家订单 REST 控制器
 * 路径前缀：/order/merchant
 */
@Slf4j
@RestController
@RequestMapping("/order/merchant")
public class MerchantOrderController {

    @Autowired
    private MerchantFulfillmentService merchantFulfillmentService;

    @Autowired
    private MerchantOrderQueryService merchantOrderQueryService;

    /**
     * GET /order/merchant/orders/{orderId} - 获取商家订单详情
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderHttpResponse<MerchantOrderData>> getOrder(@PathVariable String orderId) {
        try {
            MerchantOrderData order = merchantOrderQueryService.getMerchantOrder(orderId);
            return ResponseEntity.ok(OrderHttpResponse.ok(order));

        } catch (Exception e) {
            log.error("Get order failed: orderId={}", orderId, e);
            return ResponseEntity.status(404).body(
                OrderHttpResponse.fail(404, "Order not found: " + orderId));
        }
    }

    /**
     * POST /order/merchant/orders/{orderId}/accept - 商家同意订单
     */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<OrderHttpResponse<Void>> acceptOrder(
        @PathVariable String orderId,
        @RequestBody(required = false) MerchantAcceptOrderRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = (request != null && request.getTraceId() != null) 
                ? request.getTraceId() : UUID.randomUUID().toString();
            
            merchantFulfillmentService.merchantAcceptOrder(orderId, traceId);

            return ResponseEntity.ok(OrderHttpResponse.ok("Order accepted"));

        } catch (Exception e) {
            log.error("Accept order failed: orderId={}", orderId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Accept order failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/merchant/orders/{orderId}/ship - 商家发货
     */
    @PostMapping("/orders/{orderId}/ship")
    public ResponseEntity<OrderHttpResponse<ShipOrderData>> shipOrder(
        @PathVariable String orderId,
        @jakarta.validation.Valid @RequestBody ShipOrderRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = request.getTraceId() != null 
                ? request.getTraceId() : UUID.randomUUID().toString();

            merchantFulfillmentService.shipOrder(
                orderId, 
                request.getPackageId(), 
                request.getWaybillNo(), 
                request.getLogistics(), 
                traceId
            );

            ShipOrderData data = ShipOrderData.builder()
                .packageId(request.getPackageId())
                .waybillNo(request.getWaybillNo())
                .build();

            return ResponseEntity.ok(OrderHttpResponse.ok("Order shipped", data));

        } catch (Exception e) {
            log.error("Ship order failed: orderId={}", orderId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Ship order failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/merchant/packages/{packageId}/delivered - 包裹签收
     */
    @PostMapping("/packages/{packageId}/delivered")
    public ResponseEntity<OrderHttpResponse<Void>> packageDelivered(
        @PathVariable String packageId,
        @RequestBody(required = false) PackageDeliveredRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = (request != null && request.getTraceId() != null) 
                ? request.getTraceId() : UUID.randomUUID().toString();
            
            merchantFulfillmentService.markPackageDelivered(packageId, traceId);

            return ResponseEntity.ok(OrderHttpResponse.ok("Package delivered"));

        } catch (Exception e) {
            log.error("Mark package delivered failed: packageId={}", packageId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Mark package delivered failed: " + e.getMessage()));
        }
    }
}
