package com.github.spud.tinystore.inventory.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.domain.command.InventoryDeductCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.service.InventoryDeductDomainService;
import com.github.spud.tinystore.inventory.domain.value.DeductResult;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.infrastructure.adapter.RedisIdempotencyRepository;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockDeductMessage;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockReleaseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 库存扣减/释放事件消费者（Kafka）
 * <p>
 * 职责：
 * - 消费 topic: stock-deduct 和 stock-release
 * - 手动 ack（enable-auto-commit: false）
 * - Kafka 消费幂等（基于 orderId）+ 领域服务内部幂等（双重保障）
 * - 毒消息（JSON 解析/字段校验失败）→ 抛出不可重试异常 → 进 DLT
 * - 业务异常 → 不 ack，由 DefaultErrorHandler 重试 → 最终进 DLT
 * <p>
 * 消费流程：
 * 1. 解析 JSON 消息（失败抛出 JsonProcessingException → DLT）
 * 2. Kafka 消费幂等检查（已处理则直接 ack）
 * 3. 构造领域命令，调用领域服务
 * 4. 成功 → 标记 Kafka 消费幂等 → ack
 * 5. 业务异常 → 抛出，由 error handler 处理重试/DLT
 */
@Slf4j
@Component
public class StockDeductConsumer {

    private static final String DEDUCT_TOPIC = "stock-deduct";
    private static final String RELEASE_TOPIC = "stock-release";
    private static final String GROUP_ID = "inventory-service";

    private final InventoryDeductDomainService deductDomainService;
    private final RedisIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public StockDeductConsumer(InventoryDeductDomainService deductDomainService,
                               RedisIdempotencyRepository idempotencyRepository) {
        this.deductDomainService = deductDomainService;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 消费库存扣减事件
     * <p>
     * 手动 ack 模式：成功处理后调用 ack.acknowledge()。
     * 毒消息（解析/校验失败）：抛出不可重试异常 → DefaultErrorHandler 立即发布到 DLT。
     * 业务异常：抛出 → DefaultErrorHandler 重试 → 最终 DLT。
     *
     * @param message 消息体（JSON 字符串）
     * @param ack     手动确认对象
     */
    @KafkaListener(topics = DEDUCT_TOPIC, groupId = GROUP_ID)
    public void consumeDeduct(String message, Acknowledgment ack) {
        log.info("Received deduct message: {}", message);

        // 1. 解析消息（解析失败抛出 JsonProcessingException → DLT）
        StockDeductMessage msg;
        try {
            msg = objectMapper.readValue(message, StockDeductMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse deduct message (poison, will go to DLT): message={}", message, e);
            throw new IllegalArgumentException("Invalid JSON for deduct message", e);
        }

        // 验证必需字段（校验失败 → DLT）
        if (msg.getOrderId() == null || msg.getItems() == null || msg.getItems().isEmpty()) {
            log.error("Invalid deduct message (missing orderId or items, will go to DLT): message={}", message);
            throw new IllegalArgumentException("Missing required fields: orderId or items");
        }

        String orderId = msg.getOrderId();

        // 2. Kafka 消费幂等检查（防止重复消费）
        if (idempotencyRepository.isKafkaDeductProcessed(orderId)) {
            log.warn("Deduct already processed (Kafka idempotency hit): orderId={}", orderId);
            ack.acknowledge();
            return;
        }

        // 3. 构造领域命令
        String idempotencyKey = msg.getIdempotencyKey() != null ? msg.getIdempotencyKey() : orderId;
        InventoryDeductCommand command = InventoryDeductCommand.builder()
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .items(msg.getItems().stream()
                        .map(item -> InventoryDeductCommand.Item.builder()
                                .shopId(item.getShopId())
                                .skuId(item.getSkuId())
                                .quantity(item.getQuantity())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        // 4. 调用领域服务（内部有自己的幂等，双重保障）
        DeductResult result = deductDomainService.deduct(command);
        if (!result.isSuccess()) {
            log.error("Deduct failed (will throw for retry/DLT): orderId={}, reason={}", orderId, result.getMessage());
            // 抛出业务异常，由 error handler 处理重试
            throw new RuntimeException("Deduct failed: " + result.getMessage());
        }

        // 5. 成功 → 标记 Kafka 消费幂等 → ack
        idempotencyRepository.markKafkaDeductProcessed(orderId);
        ack.acknowledge();
        log.info("Deduct success: orderId={}, occupyPairs={}", orderId, result.getOccupyPairs());
    }

    /**
     * 消费库存释放事件
     * <p>
     * 手动 ack 模式：成功处理后调用 ack.acknowledge()。
     * 毒消息（解析/校验失败）：抛出不可重试异常 → DefaultErrorHandler 立即发布到 DLT。
     * 业务异常：抛出 → DefaultErrorHandler 重试 → 最终 DLT。
     *
     * @param message 消息体（JSON 字符串）
     * @param ack     手动确认对象
     */
    @KafkaListener(topics = RELEASE_TOPIC, groupId = GROUP_ID)
    public void consumeRelease(String message, Acknowledgment ack) {
        log.info("Received release message: {}", message);

        // 1. 解析消息（解析失败抛出 → DLT）
        StockReleaseMessage msg;
        try {
            msg = objectMapper.readValue(message, StockReleaseMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse release message (poison, will go to DLT): message={}", message, e);
            throw new IllegalArgumentException("Invalid JSON for release message", e);
        }

        // 验证必需字段（校验失败 → DLT）
        if (msg.getOrderId() == null || msg.getOccupyPairs() == null || msg.getOccupyPairs().isEmpty()) {
            log.error("Invalid release message (missing orderId or occupyPairs, will go to DLT): message={}", message);
            throw new IllegalArgumentException("Missing required fields: orderId or occupyPairs");
        }

        String orderId = msg.getOrderId();

        // 2. Kafka 消费幂等检查
        if (idempotencyRepository.isKafkaReleaseProcessed(orderId)) {
            log.warn("Release already processed (Kafka idempotency hit): orderId={}", orderId);
            ack.acknowledge();
            return;
        }

        // 3. 构造领域命令
        String idempotencyKey = msg.getIdempotencyKey() != null ? msg.getIdempotencyKey() : orderId;
        String reason = msg.getReason() != null ? msg.getReason() : "KAFKA_RELEASE";

        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .reason(reason)
                .occupyPairs(msg.getOccupyPairs().stream()
                        .map(pair -> OccupyPair.builder()
                                .shopId(pair.getShopId())
                                .skuId(pair.getSkuId())
                                .occupyId(pair.getOccupyId())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        // 4. 调用领域服务
        DeductResult result = deductDomainService.release(command);
        if (!result.isSuccess()) {
            log.error("Release failed (will throw for retry/DLT): orderId={}, reason={}", orderId, result.getMessage());
            // 抛出业务异常，由 error handler 处理重试
            throw new RuntimeException("Release failed: " + result.getMessage());
        }

        // 5. 成功 → 标记 Kafka 消费幂等 → ack
        idempotencyRepository.markKafkaReleaseProcessed(orderId);
        ack.acknowledge();
        log.info("Release success: orderId={}, reason={}", orderId, reason);
    }
}
