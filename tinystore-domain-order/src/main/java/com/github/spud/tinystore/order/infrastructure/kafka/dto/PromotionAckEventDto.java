package com.github.spud.tinystore.order.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionAckEventDto {
    private String eventId;
    private String eventType;
    private String tradeId;
    private String quoteId;
    private String reason;
    private String traceId;
}
