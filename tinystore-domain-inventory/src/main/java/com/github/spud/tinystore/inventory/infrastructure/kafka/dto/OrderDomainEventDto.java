package com.github.spud.tinystore.inventory.infrastructure.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDomainEventDto {
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String payload;
    private String traceId;
    private String occurredAt;
}
