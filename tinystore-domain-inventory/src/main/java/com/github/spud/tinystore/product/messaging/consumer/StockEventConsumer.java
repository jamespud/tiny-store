package com.github.spud.tinystore.product.messaging.consumer;

import com.github.spud.tinystore.product.messaging.events.StockEvent;
import org.springframework.stereotype.Component;

/**
 * 库存事件消费占位：根据 event.type 分派到不同处理（未实现）
 */
@Component
public class StockEventConsumer {
	public void onMessage(StockEvent event) {
		// 占位：根据 event.getType() 选择处理
	}
}

