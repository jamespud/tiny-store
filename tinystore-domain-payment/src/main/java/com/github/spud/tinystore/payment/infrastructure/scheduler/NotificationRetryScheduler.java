package com.github.spud.tinystore.payment.infrastructure.scheduler;

import com.github.spud.tinystore.infrastructure.rpc.order.OrderClient;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.PaymentOrderEntity;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.entity.RefundRecordEntity;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.PaymentOrderJpaRepository;
import com.github.spud.tinystore.payment.infrastructure.persistence.jpa.repository.RefundRecordJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知补偿调度器
 * 职责：
 * 1. 扫描已支付但通知订单域失败的 payment_order，重试通知
 * 2. 扫描已退款成功但通知订单域失败的 refund_record，重试通知
 * 
 * @author Spud
 * @date 2026/01/31
 */
@Slf4j
@Service
@EnableScheduling
public class NotificationRetryScheduler {

    @Autowired
    private PaymentOrderJpaRepository paymentOrderRepository;

    @Autowired
    private RefundRecordJpaRepository refundRecordRepository;

    @Autowired
    private OrderClient orderClient;

    @Value("${payment.scheduler.notification-retry-interval:120000}")
    private long retryInterval;

    @Value("${payment.scheduler.notification-retry-batch-size:50}")
    private int batchSize;

    /**
     * 定时重试支付成功通知（已支付但通知失败/未通知）
     * 默认每 2 分钟扫描一次
     */
    @Scheduled(fixedDelayString = "${payment.scheduler.notification-retry-interval:120000}")
    @Transactional
    public void retryPaymentSuccessNotification() {
        try {
            // 扫描：status=PAID 且 notification_status != SUCCESS 的记录
            List<PaymentOrderEntity> pendingNotifications = paymentOrderRepository
                .findByStatusAndNotificationStatusNot("PAID", "SUCCESS", PageRequest.of(0, batchSize))
                .getContent();

            if (pendingNotifications.isEmpty()) {
                log.debug("No pending payment success notifications to retry");
                return;
            }

            log.info("Found {} pending payment success notifications, retrying...", pendingNotifications.size());

            for (PaymentOrderEntity paymentOrder : pendingNotifications) {
                try {
                    // 构造通知请求
                    Map<String, Object> request = new HashMap<>();
                    request.put("paymentIntentId", paymentOrder.getPaymentIntentId());
                    request.put("paymentId", paymentOrder.getPaymentIntentId()); // 兼容旧字段
                    request.put("amountCents", paymentOrder.getAmountCents());
                    if (paymentOrder.getThirdTradeNo() != null) {
                        request.put("tradeNo", paymentOrder.getThirdTradeNo());
                    }
                    request.put("paidAt", paymentOrder.getPaidAt() != null ? paymentOrder.getPaidAt().toString() : null);

                    String idempotencyKey = "pay-retry-" + paymentOrder.getPaymentIntentId() + "-" + System.currentTimeMillis();
                    
                    // 调用订单域回调
                    orderClient.paymentCallback(paymentOrder.getTradeId(), idempotencyKey, request);

                    // 更新通知状态为成功
                    paymentOrder.setNotificationStatus("SUCCESS");
                    paymentOrder.setNotifiedAt(LocalDateTime.now());
                    paymentOrderRepository.save(paymentOrder);

                    log.info("Payment success notification retry succeeded: paymentOrderId={}, tradeId={}", 
                        paymentOrder.getPaymentOrderId(), paymentOrder.getTradeId());

                } catch (Exception e) {
                    log.error("Payment success notification retry failed: paymentOrderId={}, will retry later", 
                        paymentOrder.getPaymentOrderId(), e);
                    
                    // 更新失败状态（但不阻止继续重试）
                    paymentOrder.setNotificationStatus("FAILED");
                    paymentOrderRepository.save(paymentOrder);
                }
            }

        } catch (Exception e) {
            log.error("Error in payment success notification retry scheduler", e);
        }
    }

    /**
     * 定时重试退款结果通知（已退款成功但通知失败/未通知）
     * 默认每 2 分钟扫描一次
     */
    @Scheduled(fixedDelayString = "${payment.scheduler.notification-retry-interval:120000}")
    @Transactional
    public void retryRefundResultNotification() {
        try {
            // 扫描：refund_status=SUCCESS 且 notification_status != SUCCESS 的记录
            List<RefundRecordEntity> pendingNotifications = refundRecordRepository
                .findByRefundStatusAndNotificationStatusNot("SUCCESS", "SUCCESS", PageRequest.of(0, batchSize))
                .getContent();

            if (pendingNotifications.isEmpty()) {
                log.debug("No pending refund result notifications to retry");
                return;
            }

            log.info("Found {} pending refund result notifications, retrying...", pendingNotifications.size());

            for (RefundRecordEntity refundRecord : pendingNotifications) {
                try {
                    // 构造通知请求
                    Map<String, Object> request = new HashMap<>();
                    request.put("refundId", refundRecord.getRefundId());
                    request.put("refundStatus", "SUCCESS");
                    request.put("refundAmountCents", refundRecord.getRefundAmountCents());
                    request.put("refundAt", refundRecord.getRefundedAt() != null ? refundRecord.getRefundedAt().toString() : null);
                    if (refundRecord.getThirdRefundNo() != null) {
                        request.put("thirdRefundNo", refundRecord.getThirdRefundNo());
                    }

                    String idempotencyKey = "refund-retry-" + refundRecord.getRefundId() + "-" + System.currentTimeMillis();
                    
                    // 调用订单域退款回调
                    orderClient.refundCallback(refundRecord.getTradeId(), idempotencyKey, request);

                    // 更新通知状态为成功
                    refundRecord.setNotificationStatus("SUCCESS");
                    refundRecord.setNotifiedAt(LocalDateTime.now());
                    refundRecordRepository.save(refundRecord);

                    log.info("Refund result notification retry succeeded: refundId={}, tradeId={}", 
                        refundRecord.getRefundId(), refundRecord.getTradeId());

                } catch (Exception e) {
                    log.error("Refund result notification retry failed: refundId={}, will retry later", 
                        refundRecord.getRefundId(), e);
                    
                    // 更新失败状态（但不阻止继续重试）
                    refundRecord.setNotificationStatus("FAILED");
                    refundRecordRepository.save(refundRecord);
                }
            }

        } catch (Exception e) {
            log.error("Error in refund result notification retry scheduler", e);
        }
    }
}
