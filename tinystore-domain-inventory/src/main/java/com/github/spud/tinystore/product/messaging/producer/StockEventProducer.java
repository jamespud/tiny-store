package com.github.spud.tinystore.product.messaging.producer;

import com.github.spud.tinystore.product.messaging.events.StockEvent;

/**
 * 库存事件生产者接口（占位）
 */
public interface StockEventProducer {
	void send(StockEvent event);
}

