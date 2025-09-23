# 事件 Schema 参考（示意）

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "aggregateType": "Order",
  "aggregateId": "order-123",
  "version": 1,
  "occurredAt": "2025-09-23T12:34:56Z",
  "producerService": "order-service",
  "schemaVersion": 1,
  "traceId": "...",
  "payload": {"orderId":"order-123", "buyerId":"u1", "amount":1000},
  "headers": {"tenant":"default"}
}
```
