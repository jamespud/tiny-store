package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.AfterSaleApplicationService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.AfterSaleCaseJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 售后 REST 控制器
 * 路径前缀：/order/after-sale/cases
 */
@Slf4j
@RestController
@RequestMapping("/order/after-sale/cases")
public class AfterSaleController {

    @Autowired
    private AfterSaleApplicationService afterSaleApplicationService;

    @Autowired
    private AfterSaleCaseJpaRepository afterSaleCaseJpaRepository;

    /**
     * GET /order/after-sale/cases/{caseId} - 获取售后案件详情
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<Map<String, Object>> getCase(@PathVariable String caseId) {
        try {
            var caseEntity = afterSaleCaseJpaRepository.findByCaseId(caseId)
                .map(c -> Map.of(
                    "caseId", c.getCaseId(),
                    "tradeId", c.getTradeId(),
                    "orderId", c.getOrderId(),
                    "caseType", c.getCaseType(),
                    "caseStatus", c.getCaseStatus(),
                    "createdAt", c.getCreatedAt().toString()
                ))
                .orElseThrow(() -> new RuntimeException("AfterSale case not found: " + caseId));

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "OK");
            response.put("data", caseEntity);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get after-sale case failed: caseId={}", caseId, e);
            return buildErrorResponse(404, "AfterSale case not found: " + caseId);
        }
    }

    /**
     * POST /order/after-sale/cases - 申请售后
     *
     * Request body:
     * {
     *   "caseId": "case_001",
     *   "tradeId": "trade_001",
     *   "orderId": "order_001",
     *   "afterSaleType": "REFUND_ONLY",
     *   "reason": "Product defective",
     *   "traceId": "trace_xxx"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> applyAfterSale(
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String caseId = (String) request.get("caseId");
            String tradeId = (String) request.get("tradeId");
            String orderId = (String) request.get("orderId");
            String aftersaleType = (String) request.get("afterSaleType");
            String reason = (String) request.get("reason");
            String traceId = (String) request.getOrDefault("traceId", UUID.randomUUID().toString());

            afterSaleApplicationService.applyAfterSale(caseId, tradeId, orderId, aftersaleType, reason, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "AfterSale applied");
            response.put("data", Map.of("caseId", caseId));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Apply after-sale failed", e);
            return buildErrorResponse(500, "Apply after-sale failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/after-sale/{caseId}/approve - 商家批准售后
     *
     * Request body:
     * {
     *   "approvalNotes": "Approved",
     *   "traceId": "trace_xxx"
     * }
     */
    @PostMapping("/{caseId}/approve")
    public ResponseEntity<Map<String, Object>> approveAfterSale(
        @PathVariable String caseId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String approvalNotes = request != null ? (String) request.get("approvalNotes") : "Approved";
            String traceId = request != null ? (String) request.get("traceId") : UUID.randomUUID().toString();

            afterSaleApplicationService.approveAfterSale(caseId, approvalNotes, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "AfterSale approved");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Approve after-sale failed: caseId={}", caseId, e);
            return buildErrorResponse(500, "Approve after-sale failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/after-sale/{caseId}/reject - 商家拒绝售后
     *
     * Request body:
     * {
     *   "rejectionReason": "Product is not defective",
     *   "traceId": "trace_xxx"
     * }
     */
    @PostMapping("/{caseId}/reject")
    public ResponseEntity<Map<String, Object>> rejectAfterSale(
        @PathVariable String caseId,
        @RequestBody(required = false) Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String rejectionReason = request != null ? (String) request.get("rejectionReason") : "Rejected";
            String traceId = request != null ? (String) request.get("traceId") : UUID.randomUUID().toString();

            afterSaleApplicationService.rejectAfterSale(caseId, rejectionReason, traceId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "AfterSale rejected");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Reject after-sale failed: caseId={}", caseId, e);
            return buildErrorResponse(500, "Reject after-sale failed: " + e.getMessage());
        }
    }

    /**
     * POST /order/after-sale/cases/{caseId}/refund-succeeded - 退款成功回调
     *
     * Request body:
     * {
     *   "refundId": "refund_001",
     *   "refundAmountCents": 99999,
     *   "refundedAt": "2026-01-30T10:00:00",
     *   "traceId": "trace_xxx"
     * }
     */
    @PostMapping("/{caseId}/refund-succeeded")
    public ResponseEntity<Map<String, Object>> refundSucceeded(
        @PathVariable String caseId,
        @RequestBody Map<String, Object> request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String refundId = (String) request.get("refundId");
            String traceId = (String) request.getOrDefault("traceId", UUID.randomUUID().toString());

            // 使用 refundId 查找并完成售后退款
            afterSaleApplicationService.completeAfterSaleRefund(refundId);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "Refund completed");
            response.put("data", Map.of("caseId", caseId, "refundId", refundId));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Complete refund failed: caseId={}", caseId, e);
            return buildErrorResponse(500, "Complete refund failed: " + e.getMessage());
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
