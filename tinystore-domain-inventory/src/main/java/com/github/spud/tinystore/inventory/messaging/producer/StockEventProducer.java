package com.github.spud.tinystore.inventory.messaging.producer;

import com.github.spud.tinystore.inventory.messaging.events.StockEvent;

/**
 * 库存事件生产者接口（占位）
 */
public interface StockEventProducer {

	void send(StockEvent event);
}

