package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.MerchantFulfillmentService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
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
    private ShopOrderJpaRepository shopOrderJpaRepository;

    /**
     * GET /order/merchant/orders/{orderId} - 获取商家订单详情
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        try {
            var order = shopOrderJpaRepository.findByOrderId(orderId)
                .map(o -> Map.of(
                    "orderId", o.getOrderId(),
                    "tradeId", o.getTradeId(),
                    "shopId", o.getShopId(),
                    "sellerId", o.getSellerId(),
                    "orderStatus", o.getOrderStatus(),
                    "createdAt", o.getCreatedAt().toString()
                ))
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", order);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get order failed: orderId={}", orderId, e);
            return buildErrorResponse(404, "Order not found: " + orderId);
        }
    }

    /**
     * POST /order/merchant/orders/{orderId}/accept - 商家同意订单
     */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<Map<String, Object>> acceptOrder(
        @PathVariable String orderId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = request != null ? (String) request.get("traceId") : UUID.randomUUID().toString();
            merchantFulfillmentService.merchantAcceptOrder(orderId, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Order accepted");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Accept order failed: orderId={}", orderId, e);
            return buildErrorResponse(500, "Accept order failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/merchant/orders/{orderId}/ship - 商家发货
     *
     * Request body:
     * {
     *   "packageId": "pkg_001",
     *   "waybillNo": "SF123456789",
     *   "logistics": "SFEXPRESS",
     *   "traceId": "trace_xxx"
     * }
     */
    @PostMapping("/orders/{orderId}/ship")
    public ResponseEntity<Map<String, Object>> shipOrder(
        @PathVariable String orderId,
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String packageId = (String) request.get("packageId");
            String waybillNo = (String) request.get("waybillNo");
            String logistics = (String) request.get("logistics");
            String traceId = (String) request.getOrDefault("traceId", UUID.randomUUID().toString());

            merchantFulfillmentService.shipOrder(orderId, packageId, waybillNo, logistics, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Order shipped");
            response.put("data", Map.of("packageId", packageId, "waybillNo", waybillNo));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ship order failed: orderId={}", orderId, e);
            return buildErrorResponse(500, "Ship order failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/merchant/packages/{packageId}/delivered - 包裹签收
     */
    @PostMapping("/packages/{packageId}/delivered")
    public ResponseEntity<Map<String, Object>> packageDelivered(
        @PathVariable String packageId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = request != null ? (String) request.get("traceId") : UUID.randomUUID().toString();
            merchantFulfillmentService.markPackageDelivered(packageId, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Package delivered");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Mark package delivered failed: packageId={}", packageId, e);
            return buildErrorResponse(500, "Mark package delivered failed: " + e.getMessage());
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
