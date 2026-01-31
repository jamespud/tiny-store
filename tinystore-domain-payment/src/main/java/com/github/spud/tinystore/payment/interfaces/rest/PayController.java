package com.github.spud.tinystore.payment.interfaces.rest;

import com.github.spud.tinystore.payment.application.PaymentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付服务对外 REST API
 * 路径：/api/pay/**
 * 通过网关暴露：lb://pay-service
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
public class PayController {

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    /**
     * GET /api/pay/cashier/{paymentIntentId} - 获取收银台支付参数/链接
     * 
     * @param paymentIntentId 支付意图ID
     * @return 收银台参数（包含支付链接、JSAPI参数等）
     */
    @GetMapping("/cashier/{paymentIntentId}")
    public ResponseEntity<Map<String, Object>> getCashier(@PathVariable String paymentIntentId) {
        try {
            Map<String, Object> cashier = paymentApplicationService.getCashier(paymentIntentId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", cashier);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get cashier failed: paymentIntentId={}", paymentIntentId, e);
            return buildErrorResponse(500, "Get cashier failed: " + e.getMessage());
        }
    }

    /**
     * GET /api/pay/state/{paymentIntentId} - 查询支付状态
     * 
     * @param paymentIntentId 支付意图ID
     * @return 支付状态（UNPAID/PAID/CLOSED/EXPIRED）
     */
    @GetMapping("/state/{paymentIntentId}")
    public ResponseEntity<Map<String, Object>> queryPayState(@PathVariable String paymentIntentId) {
        try {
            Map<String, Object> state = paymentApplicationService.queryPayState(paymentIntentId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", state);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Query pay state failed: paymentIntentId={}", paymentIntentId, e);
            return buildErrorResponse(500, "Query state failed: " + e.getMessage());
        }
    }

    /**
     * POST /api/pay/close/{paymentIntentId} - 关闭支付单（订单超时/用户取消时调用）
     * 
     * @param paymentIntentId 支付意图ID
     * @return 关闭结果
     */
    @PostMapping("/close/{paymentIntentId}")
    public ResponseEntity<Map<String, Object>> closePayOrder(@PathVariable String paymentIntentId) {
        try {
            Map<String, Object> result = paymentApplicationService.closePayOrder(paymentIntentId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", result);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Close pay order failed: paymentIntentId={}", paymentIntentId, e);
            return buildErrorResponse(500, "Close order failed: " + e.getMessage());
        }
    }

    // ============ 辅助方法 ============

    private ResponseEntity<Map<String, Object>> buildErrorResponse(int code, String msg) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("msg", msg);
        return ResponseEntity.status(code >= 500 ? 500 : 400).body(response);
    }

}
