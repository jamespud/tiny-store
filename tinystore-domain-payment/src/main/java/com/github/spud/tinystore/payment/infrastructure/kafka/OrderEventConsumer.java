package com.github.spud.tinystore.payment.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.payment.application.PaymentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单事件消费者
 * 订阅 tinystore.order.general，消费订单域的命令型事件
 * 
 * @author Spud
 * @date 2026/01/30
 */
@Slf4j
@Component
public class OrderEventConsumer {

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 消费订单域事件
     * Envelope 结构：eventId, eventType, aggregateType, aggregateId, payload, traceId, occurredAt
     * 
     * @param message Kafka 消息（JSON 字符串）
     * @param ack 手动确认
     */
    @KafkaListener(topics = "${payment.kafka.topic.order-events}", groupId = "pay-service")
    public void handleOrderEvent(String message, Acknowledgment ack) {
        try {
            log.debug("Received order event: {}", message);

            // 解析 Outbox envelope
            Map<String, Object> envelope = objectMapper.readValue(message, Map.class);
            String eventType = (String) envelope.get("eventType");
            String payload = (String) envelope.get("payload");

            // 根据事件类型分发
            switch (eventType) {
                case "PAYMENT_INTENT_CREATED":
                    handlePaymentIntentCreated(payload);
                    break;
                case "REFUND_REQUESTED":
                    handleRefundRequested(payload);
                    break;
                default:
                    log.debug("Ignore event type: {}", eventType);
            }

            // 手动提交 offset
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to handle order event: message={}", message, e);
            // 消费失败不提交 offset，等待重试
            // 生产环境可配置死信队列或告警
        }
    }

    /**
     * 处理 PAYMENT_INTENT_CREATED 事件（创建支付单）
     */
    private void handlePaymentIntentCreated(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            // 兼容 paymentId 与 paymentIntentId 字段
            String paymentIntentId = data.containsKey("paymentIntentId")
                ? (String) data.get("paymentIntentId")
                : (String) data.get("paymentId");

            String tradeId = (String) data.get("tradeId");
            String buyerId = (String) data.get("buyerId");
            Long amountCents = ((Number) data.get("amountCents")).longValue();
            String payChannel = (String) data.get("payChannel");
            String expireAt = (String) data.get("expireAt");

            paymentApplicationService.createPaymentOrderFromIntent(
                paymentIntentId, tradeId, buyerId, amountCents, payChannel, expireAt
            );

        } catch (Exception e) {
            log.error("Failed to handle PAYMENT_INTENT_CREATED: payload={}", payload, e);
            throw new RuntimeException("Handle PAYMENT_INTENT_CREATED failed", e);
        }
    }

    /**
     * 处理 REFUND_REQUESTED 事件（创建退款记录并执行退款）
     */
    private void handleRefundRequested(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            String refundId = (String) data.get("refundId");
            String paymentIntentId = data.containsKey("paymentIntentId")
                ? (String) data.get("paymentIntentId")
                : null;
            String tradeId = (String) data.get("tradeId");
            Long refundAmountCents = ((Number) data.get("refundAmountCents")).longValue();

            paymentApplicationService.createRefundRecordFromRequest(
                refundId, paymentIntentId, tradeId, refundAmountCents
            );

        } catch (Exception e) {
            log.error("Failed to handle REFUND_REQUESTED: payload={}", payload, e);
            throw new RuntimeException("Handle REFUND_REQUESTED failed", e);
        }
    }

}
