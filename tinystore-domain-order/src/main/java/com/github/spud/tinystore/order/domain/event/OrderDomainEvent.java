package com.github.spud.tinystore.order.domain.event;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Data
public class OrderDomainEvent {

	private String eventId;

	private EventType eventType;

	private LocalDateTime occurredAt;

	private Object aggregateType;

	private String aggregateId;

	private Integer version;

	private Object payloadType;

	private Object payload;

	private Object dataHash;
}
