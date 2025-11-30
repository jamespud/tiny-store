package com.github.spud.tinystore.order.infrastructure.event.outbox;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

class OutboxEventPayloadSchemaTest {

    @Test
    void buildEvent_contains_v1_schema_fields() {
        OutboxEventService svc = new OutboxEventService(null, new com.fasterxml.jackson.databind.ObjectMapper());
        Map<String,Object> payload = svc.buildEvent(
                "order.fulfillment.shipped",
                "ORDER-123",
                "SUB-1",
                "TENANT-1",
                "MERCHANT-9",
                java.util.Map.of("foo","bar")
        );
        assertThat(payload.get("version")).isEqualTo("v1");
        assertThat(payload.get("eventType")).isEqualTo("order.fulfillment.shipped");
        assertThat(payload.get("aggregateId")).isEqualTo("ORDER-123");
        assertThat(payload.get("subOrderId")).isEqualTo("SUB-1");
        assertThat(payload.get("tenantId")).isEqualTo("TENANT-1");
        assertThat(payload.get("operatorId")).isEqualTo("MERCHANT-9");
        assertThat(payload.get("occurredAt")).isInstanceOf(String.class);
        assertThat(((Map<?,?>)payload.get("data")).get("foo")).isEqualTo("bar");
    }
}
