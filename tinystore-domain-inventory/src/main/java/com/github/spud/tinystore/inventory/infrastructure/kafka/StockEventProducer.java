package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.github.spud.tinystore.inventory.domain.event.StockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件发送占位：当前仅日志输出
 */
@Slf4j
@Component
public class StockEventProducer {

	public void send(StockEvent event) {
		log.info("[StockEvent] type={} shop={} sku={} reservation={} version={}", event.getType(),
			event.getShopId(), event.getSkuId(), event.getReservationId(), event.getVersion());
	}
}

