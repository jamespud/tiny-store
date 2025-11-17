# 出箱事件 Schema 与命名规范

本文约定订单模块 Outbox 事件的类型命名（event_type）、负载结构（event_payload），以及分区/去重策略，与当前实现（`OrderOutboxEventPO`、`OutboxEventService`）保持一致并作统一化约束。

## 1) event_type（主题/类型）

统一使用点分层命名，围绕订单生命周期：

- 基础生命周期
  - `order.created`
  - `order.payment.succeeded`
  - `order.accepted`
  - `order.shipped`
  - `order.delivered`
  - `order.completed`
  - `order.cancelled`

- 售后/退款
  - `order.refund.requested`
  - `order.refund.completed`

与现有代码的对应：
- `OrderApplicationService.determineTopicForEvent` 已输出：`order.created / order.payment.succeeded / order.shipped / order.completed / order.cancelled / order.refund.requested / order.refund.completed`，与本规范一致。
- 代码中个别直接写入的类型（如 `logistics.delivered`）建议统一为 `order.delivered`（后续实现时对齐）。

## 2) event_payload（JSON）

通用结构（v1）：
```json
{
  "version": "v1",
  "eventId": "<uuid>",
  "aggregateId": "<mainOrderNo>",
  "subOrderId": "<optional>",
  "occurredAt": "2025-01-01T12:00:00Z",
  "tenantId": "<tenant>",
  "operator": { "id": "<uid|opId|system>", "type": "user|merchant|system" },
  "traceId": "<trace/correlation>",
  "data": { /* 事件特定字段 */ }
}
```

事件特定字段示例：
- `order.payment.succeeded`：`{ "paymentId": "...", "amount": 12345, "currency": "CNY" }`
- `order.shipped`：`{ "packageNo": "...", "company": "SF", "trackingNo": "..." }`
- `order.delivered`：`{ "packageNo": "...", "deliveredAt": "..." }`
- `order.completed`：`{ "receiveType": "user|auto" }`
- `order.cancelled`：`{ "reason": "USER_CANCELLED|PAYMENT_TIMEOUT|MERCHANT_CANCELLED|SYSTEM_CANCELLED" }`
- `order.refund.completed`：`{ "refundId": "...", "amount": 12345 }`

版本策略：
- 从 `v1` 开始向后兼容；新增字段直接扩展 `data` 或添加可选顶层字段

## 3) Outbox 存储与表结构

- 表：`order_outbox_event`
- 字段（与实体 `OrderOutboxEventPO` 对齐）：
  - `id`（PK, uuid）
  - `order_no`（聚合主键/分区键）
  - `event_type`（见上）
  - `event_payload`（jsonb）
  - `trace_id`（可空）
  - `status`（`PENDING|SENT|FAILED`）
  - `retry_count`（int）
  - `created_at / updated_at`

索引建议：
- `idx_outbox_order_no`（`order_no`）
- `idx_outbox_status_created_at`（`status, created_at`）

## 4) 分区/去重/投递

- 分区键（Partition Key）：`aggregateId`（落库为 `order_no`）
- 去重键（Dedup Key）：`eventId`（消息端防重）
- 投递流程：
  - 本地事务内写 Outbox → 发布器定时读取 `status=PENDING` → 发送成功标记 `SENT`，失败 `retry_count+1`，超过阈值标记 `FAILED`
- 清理：`SENT` 事件按保留期（7~30 天）定期清理

## 5) 兼容性与对齐

- 现有 `determineTopicForEvent` 输出与本文一致，可继续沿用
- 后续将把直接字符串事件（如 `merchant.accept`, `logistics.delivered`）统一替换为上文标准事件类型
