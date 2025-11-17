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