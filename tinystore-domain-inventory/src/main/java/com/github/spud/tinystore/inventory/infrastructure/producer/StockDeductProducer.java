package com.github.spud.tinystore.inventory.infrastructure.producer;

import com.github.spud.tinystore.inventory.infrastructure.util.InventoryRedisManager;
import jakarta.annotation.Resource;
import lombok.Setter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockDeductProducer {

    @Setter
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String DEDUCT_TOPIC = "stock-deduct";

    private static final String RELEASE_TOPIC = "stock-release";

    public boolean produce(List<InventoryRedisManager.DeductResult> result) {
        // TODO: 发送库存扣减消息
        return false;
    }

    public boolean produceRelease(List<InventoryRedisManager.ReleaseResult> result) {
        // TODO: 发送库存释放消息
        return false;
    }
}
