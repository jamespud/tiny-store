package com.github.spud.tinystore.payment.application;

import com.github.spud.tinystore.infrastructure.rpc.order.OrderClient;
import com.github.spud.tinystore.infrastructure.rpc.order.dto.request.OrderPaymentCallbackRequest;
import com.github.spud.tinystore.infrastructure.rpc.order.dto.request.OrderRefundCallbackRequest;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.PaymentOrderEntity;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.RefundRecordEntity;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.PaymentOrderJpaRepository;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.RefundRecordJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付应用服务
 * 核心职责：
 * 1. 消费订单事件创建支付单/退款记录
 * 2. 提供对外查询/关闭支付单API
 * 3. 通知订单域支付结果/退款结果
 * 
 * @author Spud
 * @date 2025/8/15
 */
@Slf4j
@Service
public class PaymentApplicationService {

    @Autowired
    private PaymentOrderJpaRepository paymentOrderRepository;

    @Autowired
    private RefundRecordJpaRepository refundRecordRepository;

    @Autowired
    private OrderClient orderClient;

    /**
     * 从订单事件创建支付单（PAYMENT_INTENT_CREATED 事件消费）
     * 
     * @param paymentIntentId 支付意图ID（订单域传入，幂等主键）
     * @param tradeId 交易ID
     * @param buyerId 买家ID
     * @param amountCents 支付金额（分）
     * @param payChannel 支付渠道
     * @param expireAt 支付超时时间
     */
    @Transactional
    public void createPaymentOrderFromIntent(String paymentIntentId, String tradeId, 
                                             String buyerId, Long amountCents, 
                                             String payChannel, String expireAt) {
        try {
            // 幂等检查：paymentIntentId 唯一
            if (paymentOrderRepository.findByPaymentIntentId(paymentIntentId).isPresent()) {
                log.info("PaymentOrder already exists (idempotent): paymentIntentId={}", paymentIntentId);
                return;
            }

            // 创建支付单
            String paymentOrderId = UUID.randomUUID().toString();
            LocalDateTime expireAtTime = expireAt != null ? LocalDateTime.parse(expireAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;

            PaymentOrderEntity paymentOrder = PaymentOrderEntity.builder()
                .paymentOrderId(paymentOrderId)
                .paymentIntentId(paymentIntentId)
                .tradeId(tradeId)
                .buyerId(buyerId)
                .amountCents(amountCents)
                .payChannel(payChannel != null ? payChannel : "DEFAULT")
                .status("UNPAID")
                .expireAt(expireAtTime)
                .createdAt(LocalDateTime.now())
                .build();

            paymentOrderRepository.save(paymentOrder);

            log.info("PaymentOrder created: paymentOrderId={}, paymentIntentId={}, tradeId={}", 
                paymentOrderId, paymentIntentId, tradeId);

        } catch (Exception e) {
            log.error("Failed to create PaymentOrder from intent: paymentIntentId={}", paymentIntentId, e);
            throw new RuntimeException("Create PaymentOrder failed", e);
        }
    }

    /**
     * 从订单事件创建退款记录（REFUND_REQUESTED 事件消费）
     * 
     * @param refundId 退款单号（订单域传入，幂等主键）
     * @param paymentIntentId 支付意图ID
     * @param tradeId 交易ID
     * @param refundAmountCents 退款金额
     */
    @Transactional
    public void createRefundRecordFromRequest(String refundId, String paymentIntentId, 
                                              String tradeId, Long refundAmountCents) {
        try {
            // 幂等检查：refundId 唯一
            if (refundRecordRepository.findByRefundId(refundId).isPresent()) {
                log.info("RefundRecord already exists (idempotent): refundId={}", refundId);
                return;
            }

            // 查询支付单
            PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("PaymentOrder not found: " + paymentIntentId));

            // 创建退款记录
            RefundRecordEntity refundRecord = RefundRecordEntity.builder()
                .refundId(refundId)
                .paymentIntentId(paymentIntentId)
                .paymentOrderId(paymentOrder.getPaymentOrderId())
                .tradeId(tradeId)
                .refundAmountCents(refundAmountCents)
                .refundStatus("REQUESTED")
                .requestedAt(LocalDateTime.now())
                .build();

            refundRecordRepository.save(refundRecord);

            log.info("RefundRecord created: refundId={}, paymentOrderId={}, amountCents={}", 
                refundId, paymentOrder.getPaymentOrderId(), refundAmountCents);

            // 短期：直接模拟退款成功并通知订单域
            // 长期：此处调用第三方退款接口，异步回调时再通知订单
            simulateRefundSuccessAndNotifyOrder(refundId, tradeId, refundAmountCents);

        } catch (Exception e) {
            log.error("Failed to create RefundRecord: refundId={}", refundId, e);
            throw new RuntimeException("Create RefundRecord failed", e);
        }
    }

    /**
     * 短期模拟：直接标记退款成功并通知订单域
     * 长期：此逻辑应由第三方退款回调触发
     */
    private void simulateRefundSuccessAndNotifyOrder(String refundId, String tradeId, Long refundAmountCents) {
        try {
            // 更新退款状态
            RefundRecordEntity refundRecord = refundRecordRepository.findByRefundId(refundId)
                .orElseThrow(() -> new RuntimeException("RefundRecord not found: " + refundId));

            refundRecord.setRefundStatus("SUCCESS");
            refundRecord.setRefundedAt(LocalDateTime.now());
            refundRecordRepository.save(refundRecord);

            // 通知订单域退款成功
            notifyOrderRefundResult(refundId, tradeId, refundAmountCents, "SUCCESS");

        } catch (Exception e) {
            log.error("Failed to simulate refund success: refundId={}", refundId, e);
        }
    }

    /**
     * 通知订单域退款结果
     * 
     * @param refundId 退款单号
     * @param tradeId 交易ID
     * @param refundAmountCents 退款金额
     * @param refundStatus 退款状态（SUCCESS/FAIL/PROCESSING）
     */
    public void notifyOrderRefundResult(String refundId, String tradeId, 
                                        Long refundAmountCents, String refundStatus) {
        try {
            OrderRefundCallbackRequest request = OrderRefundCallbackRequest.builder()
                .refundId(refundId)
                .refundStatus(refundStatus)
                .refundAmountCents(refundAmountCents)
                .traceId(LocalDateTime.now().toString())
                .build();

            String idempotencyKey = "refund-notify-" + refundId;
            orderClient.refundCallback(tradeId, idempotencyKey, request);

            // 通知成功，更新 refund_record 状态
            refundRecordRepository.findByRefundId(refundId).ifPresent(rr -> {
                rr.setNotificationStatus("SUCCESS");
                rr.setNotifiedAt(LocalDateTime.now());
                refundRecordRepository.save(rr);
            });

            log.info("Notified order refund result: tradeId={}, refundId={}, status={}", 
                tradeId, refundId, refundStatus);

        } catch (Exception e) {
            log.error("Failed to notify order refund result: refundId={}", refundId, e);
            
            // 通知失败，标记 FAILED（由补偿任务重试）
            refundRecordRepository.findByRefundId(refundId).ifPresent(rr -> {
                rr.setNotificationStatus("FAILED");
                refundRecordRepository.save(rr);
            });
        }
    }

    /**
     * 查询支付状态（对外API）
     * 
     * @param paymentIntentId 支付意图ID
     * @return 支付状态信息
     */
    public Map<String, Object> queryPayState(String paymentIntentId) {
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
            .orElse(null);

        Map<String, Object> result = new HashMap<>();
        if (paymentOrder == null) {
            result.put("status", "NOT_FOUND");
        } else {
            result.put("status", paymentOrder.getStatus());
            result.put("paymentOrderId", paymentOrder.getPaymentOrderId());
            result.put("amountCents", paymentOrder.getAmountCents());
        }
        return result;
    }

    /**
     * 关闭支付单（对外API，订单超时/用户取消时调用）
     * 
     * @param paymentIntentId 支付意图ID
     * @return 关闭结果
     */
    @Transactional
    public Map<String, Object> closePayOrder(String paymentIntentId) {
        try {
            PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("PaymentOrder not found: " + paymentIntentId));

            if ("PAID".equals(paymentOrder.getStatus())) {
                throw new RuntimeException("Cannot close paid order");
            }

            paymentOrder.setStatus("CLOSED");
            paymentOrder.setClosedAt(LocalDateTime.now());
            paymentOrderRepository.save(paymentOrder);

            log.info("PaymentOrder closed: paymentIntentId={}", paymentIntentId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("paymentOrderId", paymentOrder.getPaymentOrderId());
            return result;

        } catch (Exception e) {
            log.error("Failed to close PaymentOrder: paymentIntentId={}", paymentIntentId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 通知订单域支付成功
     * 
     * @param paymentIntentId 支付意图ID
     * @param tradeId 交易ID
     * @param amountCents 支付金额
     * @param thirdTradeNo 第三方流水号
     */
    public void notifyOrderPaymentSuccess(String paymentIntentId, String tradeId, 
                                          Long amountCents, String thirdTradeNo) {
        try {
            OrderPaymentCallbackRequest request = OrderPaymentCallbackRequest.builder()
                .paymentIntentId(paymentIntentId)
                .amountCents(amountCents)
                .traceId(LocalDateTime.now().toString())
                .build();

            String idempotencyKey = "pay-notify-" + paymentIntentId;
            orderClient.paymentCallback(tradeId, idempotencyKey, request);

            // 通知成功，更新 payment_order 状态
            paymentOrderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(po -> {
                po.setNotificationStatus("SUCCESS");
                po.setNotifiedAt(LocalDateTime.now());
                paymentOrderRepository.save(po);
            });

            log.info("Notified order payment success: tradeId={}, paymentIntentId={}", 
                tradeId, paymentIntentId);

        } catch (Exception e) {
            log.error("Failed to notify order payment success: paymentIntentId={}", paymentIntentId, e);
            
            // 通知失败，标记 FAILED（由补偿任务重试）
            paymentOrderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(po -> {
                po.setNotificationStatus("FAILED");
                paymentOrderRepository.save(po);
            });
        }
    }

    /**
     * 获取收银台参数（对外API，短期返回最小字段）
     * 
     * @param paymentIntentId 支付意图ID
     * @return 收银台参数
     */
    public Map<String, Object> getCashier(String paymentIntentId) {
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPaymentIntentId(paymentIntentId)
            .orElseThrow(() -> new RuntimeException("PaymentOrder not found: " + paymentIntentId));

        Map<String, Object> result = new HashMap<>();
        result.put("paymentOrderId", paymentOrder.getPaymentOrderId());
        result.put("amountCents", paymentOrder.getAmountCents());
        result.put("payChannel", paymentOrder.getPayChannel());
        result.put("status", paymentOrder.getStatus());
        result.put("expireAt", paymentOrder.getExpireAt() != null ? paymentOrder.getExpireAt().toString() : null);
        
        // 短期返回模拟收银台链接，长期应对接具体渠道生成真实参数
        result.put("cashierUrl", "https://mock-cashier.example.com/pay/" + paymentOrder.getPaymentOrderId());

        return result;
    }

}

