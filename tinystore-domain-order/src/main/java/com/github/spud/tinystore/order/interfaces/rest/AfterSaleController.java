package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.AfterSaleApplicationService;
import com.github.spud.tinystore.order.application.query.AfterSaleQueryService;
import com.github.spud.tinystore.order.interfaces.dto.request.*;
import com.github.spud.tinystore.order.interfaces.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private AfterSaleQueryService afterSaleQueryService;

    /**
     * GET /order/after-sale/cases/{caseId} - 获取售后案件详情
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<OrderHttpResponse<AfterSaleCaseData>> getCase(@PathVariable String caseId) {
        try {
            AfterSaleCaseData caseData = afterSaleQueryService.getAfterSaleCase(caseId);
            return ResponseEntity.ok(OrderHttpResponse.ok(caseData));

        } catch (Exception e) {
            log.error("Get after-sale case failed: caseId={}", caseId, e);
            return ResponseEntity.status(404).body(
                OrderHttpResponse.fail(404, "AfterSale case not found: " + caseId));
        }
    }

    /**
     * POST /order/after-sale/cases - 申请售后
     */
    @PostMapping
    public ResponseEntity<OrderHttpResponse<ApplyAfterSaleData>> applyAfterSale(
        @RequestBody ApplyAfterSaleRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String traceId = request.getTraceId() != null 
                ? request.getTraceId() : UUID.randomUUID().toString();

            afterSaleApplicationService.applyAfterSale(
                request.getCaseId(), 
                request.getTradeId(), 
                request.getOrderId(), 
                request.getAfterSaleType(), 
                request.getReason(), 
                traceId
            );

            ApplyAfterSaleData data = ApplyAfterSaleData.builder()
                .caseId(request.getCaseId())
                .build();

            return ResponseEntity.ok(OrderHttpResponse.ok("AfterSale applied", data));

        } catch (Exception e) {
            log.error("Apply after-sale failed", e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Apply after-sale failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/after-sale/{caseId}/approve - 商家批准售后
     */
    @PostMapping("/{caseId}/approve")
    public ResponseEntity<OrderHttpResponse<Void>> approveAfterSale(
        @PathVariable String caseId,
        @RequestBody(required = false) ApproveAfterSaleRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String approvalNotes = (request != null && request.getApprovalNotes() != null) 
                ? request.getApprovalNotes() : "Approved";
            String traceId = (request != null && request.getTraceId() != null)
                ? request.getTraceId() : UUID.randomUUID().toString();

            afterSaleApplicationService.approveAfterSale(caseId, approvalNotes, traceId);

            return ResponseEntity.ok(OrderHttpResponse.ok("AfterSale approved"));

        } catch (Exception e) {
            log.error("Approve after-sale failed: caseId={}", caseId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Approve after-sale failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/after-sale/{caseId}/reject - 商家拒绝售后
     */
    @PostMapping("/{caseId}/reject")
    public ResponseEntity<OrderHttpResponse<Void>> rejectAfterSale(
        @PathVariable String caseId,
        @RequestBody(required = false) RejectAfterSaleRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            String rejectionReason = (request != null && request.getRejectionReason() != null) 
                ? request.getRejectionReason() : "Rejected";
            String traceId = (request != null && request.getTraceId() != null)
                ? request.getTraceId() : UUID.randomUUID().toString();

            afterSaleApplicationService.rejectAfterSale(caseId, rejectionReason, traceId);

            return ResponseEntity.ok(OrderHttpResponse.ok("AfterSale rejected"));

        } catch (Exception e) {
            log.error("Reject after-sale failed: caseId={}", caseId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Reject after-sale failed: " + e.getMessage()));
        }
    }

    /**
     * POST /order/after-sale/cases/{caseId}/refund-succeeded - 退款成功回调
     */
    @PostMapping("/{caseId}/refund-succeeded")
    public ResponseEntity<OrderHttpResponse<RefundSucceededData>> refundSucceeded(
        @PathVariable String caseId,
        @RequestBody RefundSucceededRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        try {
            // 使用 refundId 查找并完成售后退款
            afterSaleApplicationService.completeAfterSaleRefund(request.getRefundId());

            RefundSucceededData data = RefundSucceededData.builder()
                .caseId(caseId)
                .refundId(request.getRefundId())
                .build();

            return ResponseEntity.ok(OrderHttpResponse.ok("Refund completed", data));

        } catch (Exception e) {
            log.error("Complete refund failed: caseId={}", caseId, e);
            return ResponseEntity.status(500).body(
                OrderHttpResponse.fail(500, "Complete refund failed: " + e.getMessage()));
        }
    }
}
