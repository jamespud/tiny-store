package com.github.spud.tinystore.inventory.infrastructure.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockDeductMessage;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockReleaseMessage;
import jakarta.annotation.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 库存扣减/释放事件生产者（Kafka）
 * <p>
 * 职责：
 * - 发送库存扣减事件到 topic: stock-deduct
 * - 发送库存释放事件到 topic: stock-release
 * - 使用 orderId 作为 partition key，保证同一订单的事件顺序消费
 * - JSON 序列化 + 异步发送 + 失败日志
 */
@Slf4j
@Service
public class StockDeductProducer {

    @Setter
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEDUCT_TOPIC = "stock-deduct";
    private static final String RELEASE_TOPIC = "stock-release";

    /**
     * 发送库存扣减事件
     * <p>
     * 消息格式：JSON，包含 orderId + items
     * <p>
     * Partition key：orderId（保证同一订单的扣减/释放事件按顺序消费）
     *
     * @param message 扣减消息
     */
    public void publishDeduct(StockDeductMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.error("Cannot publish deduct: message or orderId is null");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            String partitionKey = message.getOrderId();

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(DEDUCT_TOPIC, partitionKey, json);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published deduct event: topic={}, partition={}, offset={}, orderId={}",
                            DEDUCT_TOPIC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message.getOrderId());
                } else {
                    log.error("Failed to publish deduct event: orderId={}", message.getOrderId(), ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize deduct message: orderId={}", message.getOrderId(), e);
        }
    }

    /**
     * 发送库存释放事件
     * <p>
     * 消息格式：JSON，包含 orderId + occupyPairs + reason
     * <p>
     * Partition key：orderId
     *
     * @param message 释放消息
     */
    public void publishRelease(StockReleaseMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.error("Cannot publish release: message or orderId is null");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            String partitionKey = message.getOrderId();

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(RELEASE_TOPIC, partitionKey, json);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published release event: topic={}, partition={}, offset={}, orderId={}",
                            RELEASE_TOPIC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message.getOrderId());
                } else {
                    log.error("Failed to publish release event: orderId={}", message.getOrderId(), ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize release message: orderId={}", message.getOrderId(), e);
        }
    }

    // ========================== 废弃方法（向后兼容） ==========================

    /**
     * @deprecated 使用 {@link #publishDeduct(StockDeductMessage)} 代替
     */
    @Deprecated
    public boolean produce(Object result) {
        log.warn("produce() is deprecated, use publishDeduct() instead");
        return false;
    }

    /**
     * @deprecated 使用 {@link #publishRelease(StockReleaseMessage)} 代替
     */
    @Deprecated
    public boolean produceRelease(Object result) {
        log.warn("produceRelease() is deprecated, use publishRelease() instead");
        return false;
    }
}
