package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.enums.AfterSaleType;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.model.AfterSaleCase;
import com.github.spud.tinystore.order.domain.repository.AfterSaleCaseRepository;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 售后应用服务（申请、批准、拒绝、退款）
 */
@Slf4j
@Service
public class AfterSaleApplicationService {

    @Autowired
    private AfterSaleCaseRepository afterSaleCaseRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private PaymentIntentJpaRepository paymentIntentJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 申请售后
     *
     * @param caseId 售后案件ID
     * @param tradeId 交易ID
     * @param orderId 子单ID
     * @param aftersaleType 售后类型（REFUND_ONLY/RETURN_AND_REFUND/EXCHANGE）
     * @param reason 申请原因
     * @param traceId 追踪ID
     */
    @Transactional
    public void applyAfterSale(String caseId, String tradeId, String orderId, 
                              String aftersaleType, String reason, String traceId) throws Exception {
        try {
            // 检查是否已存在该售后案件
            AfterSaleCase existing = afterSaleCaseRepository.findByCaseId(caseId).orElse(null);
            if (existing != null && AfterSaleStatus.APPLIED.equals(existing.getCaseStatus())) {
                log.info("AfterSale case already exists: caseId={}", caseId);
                return;
            }

            AfterSaleCase caseEntity = AfterSaleCase.builder()
                .caseId(caseId)
                .tradeId(tradeId)
                .orderId(orderId)
                .caseType(AfterSaleType.valueOf(aftersaleType))
                .caseStatus(AfterSaleStatus.APPLIED)
                .createdAt(LocalDateTime.now())
                .build();
            caseEntity = afterSaleCaseRepository.save(caseEntity);

            // 写入 Outbox 事件
            OrderDomainEvent appliedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.AFTER_SALE_APPLIED)
                .aggregateType("AFTER_SALE")
                .aggregateId(caseId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "caseId", caseId,
                    "tradeId", tradeId,
                    "orderId", orderId,
                    "type", aftersaleType,
                    "reason", reason
                )))
                .build();
            outboxEventService.saveEvent(appliedEvent);

            log.info("AfterSale applied: caseId={}, type={}", caseId, aftersaleType);

        } catch (Exception e) {
            log.error("ApplyAfterSale failed: caseId={}", caseId, e);
            throw e;
        }
    }

    /**
     * 商家批准售后申请
     *
     * @param caseId 售后案件ID
     * @param approvalNotes 批准备注
     * @param traceId 追踪ID
     */
    @Transactional
    public void approveAfterSale(String caseId, String approvalNotes, String traceId) throws Exception {
        try {
            AfterSaleCase caseEntity = afterSaleCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new DomainConflictException("AFTER_SALE_NOT_FOUND",
                    "AfterSale case not found: " + caseId));

            // 使用聚合根方法批准
            caseEntity.approve();
            afterSaleCaseRepository.save(caseEntity);

            // 写入 Outbox 事件
            OrderDomainEvent approvedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.AFTER_SALE_APPROVED)
                .aggregateType("AFTER_SALE")
                .aggregateId(caseId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "caseId", caseId,
                    "approvalNotes", approvalNotes
                )))
                .build();
            outboxEventService.saveEvent(approvedEvent);

            log.info("AfterSale approved: caseId={}", caseId);

        } catch (Exception e) {
            log.error("ApproveAfterSale failed: caseId={}", caseId, e);
            throw e;
        }
    }

    /**
     * 商家拒绝售后申请
     *
     * @param caseId 售后案件ID
     * @param rejectionReason 拒绝原因
     * @param traceId 追踪ID
     */
    @Transactional
    public void rejectAfterSale(String caseId, String rejectionReason, String traceId) {
        try {
            AfterSaleCase caseEntity = afterSaleCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new DomainConflictException("AFTER_SALE_NOT_FOUND",
                    "AfterSale case not found: " + caseId));

            // 使用聚合根方法拒绝
            caseEntity.reject();
            afterSaleCaseRepository.save(caseEntity);

            log.info("AfterSale rejected: caseId={}", caseId);

        } catch (Exception e) {
            log.error("RejectAfterSale failed: caseId={}", caseId, e);
            throw e;
        }
    }

    /**
     * 处理退款（售后批准后触发）
     *
     * @param caseId 售后案件ID
     * @param refundId 退款ID（与支付 paymentId 不同）
     * @param refundAmountCents 退款金额
     * @param traceId 追踪ID
     */
    @Transactional
    public void processAfterSaleRefund(String caseId, String refundId, 
                                      Long refundAmountCents, String traceId) throws Exception {
        try {
            AfterSaleCase caseEntity = afterSaleCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new DomainConflictException("AFTER_SALE_NOT_FOUND",
                    "AfterSale case not found: " + caseId));

            // 使用聚合根方法开始退款
            caseEntity.startRefunding(refundId, refundAmountCents);
            afterSaleCaseRepository.save(caseEntity);

            // 写入 Outbox 事件（退款请求）
            // 需要查询 PaymentIntent 以获取 paymentIntentId（供支付域消费事件使用）
            String paymentIntentId = null;
            try {
                var paymentIntent = paymentIntentJpaRepository.findByTradeId(caseEntity.getTradeId());
                if (paymentIntent.isPresent()) {
                    paymentIntentId = paymentIntent.get().getPaymentId();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch paymentIntentId for tradeId={}", caseEntity.getTradeId(), e);
            }
            
            OrderDomainEvent refundRequestEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.REFUND_REQUESTED)
                .aggregateType("AFTER_SALE")
                .aggregateId(caseId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "caseId", caseId,
                    "refundId", refundId,
                    "refundAmountCents", refundAmountCents,
                    "tradeId", caseEntity.getTradeId(),
                    "paymentIntentId", paymentIntentId != null ? paymentIntentId : "" // 补齐支付意图ID
                )))
                .build();
            outboxEventService.saveEvent(refundRequestEvent);

            log.info("AfterSale refund requested: caseId={}, refundId={}, amount={}", 
                caseId, refundId, refundAmountCents);

        } catch (Exception e) {
            log.error("ProcessAfterSaleRefund failed: caseId={}", caseId, e);
            throw e;
        }
    }

    /**
     * 完成售后退款（退款成功后调用）
     *
     * @param refundId 退款ID
     */
    @Transactional
    public void completeAfterSaleRefund(String refundId) throws Exception {
        try {
            AfterSaleCase caseEntity = afterSaleCaseRepository.findByRefundId(refundId)
                .orElseThrow(() -> new DomainConflictException("AFTER_SALE_NOT_FOUND",
                    "AfterSale case not found for refundId: " + refundId));

            // 使用聚合根方法完成退款
            caseEntity.markAsRefunded(refundId);
            afterSaleCaseRepository.save(caseEntity);

            // 写入完成事件
            OrderDomainEvent refundCompletedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.REFUND_SUCCEEDED)
                .aggregateType("AFTER_SALE")
                .aggregateId(caseEntity.getCaseId())
                .occurredAt(LocalDateTime.now())
                .traceId(null)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "caseId", caseEntity.getCaseId(),
                    "refundId", refundId
                )))
                .build();
            outboxEventService.saveEvent(refundCompletedEvent);

            log.info("AfterSale refund completed: caseId={}, refundId={}", 
                caseEntity.getCaseId(), refundId);

        } catch (Exception e) {
            log.error("CompleteAfterSaleRefund failed: refundId={}", refundId, e);
            throw e;
        }
    }
}
