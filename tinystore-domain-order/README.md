# tinystore-domain-order 模块文档

本模块负责订单领域的接口、应用服务、状态机与出箱事件发布。本文档概述幂等策略、错误码映射、事件与状态机、出箱事件载荷 Schema、指标以及多租户上下文透传约定。

## 幂等键策略
- 用户取消：`cancel:{orderId}:{userId}:{reasonHash}`（`reasonHash = md5(reasonType + "|" + reasonText) 前8位`）
- 商家接单：`merchant_accept:{orderId}:{operatorId}`
- 发货：`ship:{orderId}:{packageNo}`
- 妥投确认：`delivered_confirm:{orderId}:{packageNo}`
- 取消审批/拒绝：`merchant_cancel_decision:{orderId}:{operatorId}:{decision}`
- 命中时：回放已保存结果；未命中：执行业务并保存结果。

## 错误码与 HTTP 映射
- 403 Forbidden → `ORDER-4030`（权限不足）
- 404 Not Found → `ORDER-4040`（资源不存在）
- 409 Conflict → `ORDER-4090`（并发/版本冲突）
- 422 Unprocessable Entity → `ORDER-4220`（状态非法/命令不可执行）
- 统一响应字段：`timestamp`、`errorCode`、`message`、`traceId`、`path`。

## 状态机事件与流程概览
- `PAYMENT_SUCCEEDED` → `order.payment.succeeded`
- `MERCHANT_ACCEPTED` → `order.lifecycle.changed`
- `FULFILLMENT_STARTED`/`GOODS_SHIPPED`/`GOODS_DELIVERED` → `order.fulfillment.*`
- `GOODS_RECEIVED` → `order.received`
- `USER_CANCELLED`/`SYSTEM_CANCELLED`/`PAYMENT_TIMEOUT` → `order.cancelled`
- `AUTO_COMPLETED` → `order.lifecycle.changed`
- Guards 示例：`canPay`、`canShip`、`canConfirmDelivery`、`canReceive`、`canCancel`
- Actions 统一：原子更新 → 构造 Outbox 事件 → 记录审计 → 指标上报（失败计数）。

## Outbox 事件载荷 Schema（v1）
示例：
```json
{
  "version": "v1",
  "eventType": "order.fulfillment.shipped",
  "aggregateId": "ORDER-123",
  "subOrderId": "SUB-1",
  "occurredAt": "2025-01-01T12:00:00Z",
  "tenantId": "TENANT-1",
  "operatorId": "MERCHANT-9",
  "traceId": "TRACE-xyz",
  "data": {
    "packageNo": "PKG-0001",
    "carrier": "SF"
  }
}
```
- 分区键：`aggregateId`
- 去重建议：`eventId` 结合 (`aggregateId` + `eventType` + `occurredAt` 秒级)；消费方需容忍重放。

## 指标（Micrometer）
- `order_idempotency_hit_total` / `order_idempotency_miss_total`
- `order_state_machine_failure_total`
- `order_outbox_publish_latency`（Timer）
- `order_http_error_total{status=...}`

## 多租户与 MDC 透传
- 控制器入参提取 `tenantId` 与 `actorId`，写入 `MDC(tenantId, actorId, traceId)`。
- 出箱事件与审计均包含 `tenantId`、`operatorId/actorId`、`traceId`。
- 建议在过滤器或 finally 中清理 MDC（`MDC.clear()`）。

## 废弃接口说明
- 已移除旧支付回调控制器 `OrderPaymentCallbackController`（如仍存在，后续将物理删除）。统一入口以当前 REST 控制器为准。

## 测试与运行
- 仅运行订单模块测试：
```bash
mvn -q -pl tinystore-domain-order -am test
```

## 变更记录
- 详见 `CHANGELOG_ORDER_ENHANCEMENTS.md`。
# 订单模块（tinystore-domain-order）

本模块涵盖订单主流程的接口、状态流转、出箱事件与幂等/错误语义约定。本文为快速总览，详细 Schema 见 `docs/outbox-event-schema.md`。

## API 概览

- 用户侧（`interfaces/rest/UserOrderController`）
  - `POST /order/user/submit/preview`
  - `POST /order/user/submit/apply`（带 `idempotentKey`）
  - `POST /order/user/cancel/preview`
  - `POST /order/user/cancel/apply`
  - `POST /order/user/confirm-receipt`
  - `POST /order/user/after-sale/apply`

- 商家侧（`interfaces/rest/MerchantOrderController`）
  - `POST /order/merchant/order/receive`
  - `POST /order/merchant/cancel/approve`
  - `POST /order/merchant/cancel/reject`
  - `POST /order/merchant/ship`
  - `POST /order/merchant/delivery/confirm`

- 内部回调（`interfaces/rest/InternalOrderController`）
  - `POST /order/internal/payment/success`
  - `POST /order/internal/logistics/delivered`
  - `POST /order/internal/refund/success`
  - `POST /order/internal/timeout/unpaid-cancel`
  - `POST /order/internal/auto/complete`
  - `POST /order/internal/auto/await-fulfillment`

## 幂等与错误语义

- 幂等键：
  - 用户提交流程/取消/收货/售后：按 `orderId` + 业务维度生成；回放成功结果
  - 内部回调：按第三方交易/事件 ID 去重
- 错误码：`ORDER-xxxx` 前缀；409 冲突用于乐观锁；统一 `@ControllerAdvice` 映射

## 状态流转与事件

- 核心事件映射（节选）：
  - 支付成功 → `order.payment.succeeded`
  - 发货 → `order.shipped`；妥投 → `order.delivered`
  - 收货完成 → `order.completed`；取消 → `order.cancelled`
  - 售后申请/完成 → `order.refund.requested|completed`
- 出箱事件 Schema：见 `docs/outbox-event-schema.md`
  - 载荷包含：`version,eventId,aggregateId,occurredAt,tenantId,operator,traceId,data{...}`

## 观测与审计

- 审计：在应用服务入口记录 `tenantId/userId/traceId`、命令与结果
- 指标：幂等冲突、处理成功/失败计数；Outbox 滞留/发布延迟

## 运行与构建

仅构建订单模块：

```bash
mvn -q -pl tinystore-domain-order -am -DskipTests package
```

仅运行订单模块测试：

```bash
mvn -q -pl tinystore-domain-order -am test
```

按名称选择性运行订单模块测试（示例）：

```bash
mvn -q -pl tinystore-domain-order -am -Dtest=*Order*ControllerTest test
```

仅运行新增的 Outbox 测试：

```bash
mvn -q -pl tinystore-domain-order -am -Dtest=OutboxEventServiceTest,OutboxEventPublisherTest test
```

## 参考

- 出箱事件规范：`tinystore-domain-order/docs/outbox-event-schema.md`
- 数据库迁移脚本：`tinystore-domain-order/src/main/resources/db/migration/`