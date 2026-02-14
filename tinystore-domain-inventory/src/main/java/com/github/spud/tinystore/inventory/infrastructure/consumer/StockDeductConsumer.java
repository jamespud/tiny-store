package com.github.spud.tinystore.inventory.infrastructure.consumer;

import org.springframework.kafka.annotation.KafkaListener;

public class StockDeductConsumer {
    
    private static final String DEDUCT_TOPIC = "stock-deduct";
    
    private static final String RELEASE_TOPIC = "stock-release";
    
    @KafkaListener(topics = DEDUCT_TOPIC, groupId = "inventory-service")
    public void consume(String message) {
        // TODO: 批量处理库存扣减消息
        // 1. 确认order存在且未超时
        // 2. 确认库存扣减记录不存在或未处理过
        // 3. 批量扣减库存
    }
    
    @KafkaListener(topics = RELEASE_TOPIC, groupId = "inventory-service")
    public void consumeRelease(String message) {
        // TODO: 批量处理库存释放消息
        // 1. 确认order已超时或已取消
        // 2. 确认release记录不存在或未处理过
        // 3. 批量释放库存
    }
}
