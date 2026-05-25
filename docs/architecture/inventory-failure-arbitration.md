# 库存失败裁决规则（Inventory Failure Arbitration）

> 版本：1.0  
> 所属 RFC：[RFC-001](../rfcs/RFC-001-canonical-inventory-reservation.md)

---

## 1. Confirm 冲突裁决

| 当前 reservation 状态 | confirm 请求结果 | Order 侧动作 | 后续补偿 |
|-----------------------|-----------------|--------------|----------|
| `PRE_DEDUCTED` | 成功→`CONFIRMED` | 发布 `TRADE_PAID`/`ORDER_PAID` | 无需补偿 |
| `CONFIRMED` | 幂等成功 | 发布 `TRADE_PAID`/`ORDER_PAID` | 无需补偿 |
| `RELEASED` | deterministic conflict | 仅发布 `INVENTORY_CONFIRM_CONFLICT` | 人工或专项补偿流程 |
| `EXPIRED` | deterministic conflict | 仅发布 `INVENTORY_CONFIRM_CONFLICT` | 人工或专项补偿流程 |

**强制约束**：
- `RELEASED`/`EXPIRED` 冲突时，Inventory 服务禁止偷偷把状态改为 `CONFIRMED`
- Order 服务禁止在 conflict 结果下发布 `TRADE_PAID` 或 `ORDER_PAID`
- `INVENTORY_CONFIRM_CONFLICT` 事件必须包含：tradeId、orderId、occupyPairs、conflictReason、occurredAt

## 2. Redis 补偿失败裁决

| 场景 | DB 状态 | Redis 状态 | 处理规则 |
|------|---------|-----------|----------|
| reserve 成功，Redis 准入失败 | `PRE_DEDUCTED` 已写 | 未扣减 | 打告警，补偿任务回补 Redis（不回滚 DB）|
| release 成功，Redis 回补失败 | `RELEASED` 已写 | 准入计数未恢复 | 打告警，补偿任务补 Redis（不回滚 DB）|
| expire 成功，Redis 回补失败 | `EXPIRED` 已写 | 准入计数未恢复 | 打告警，补偿任务补 Redis（不回滚 DB）|
| confirm 成功，Redis 无动作 | `CONFIRMED` 已写 | 准入计数已消费 | 正常路径，无需补偿 |

**裁决原则**：
1. DB 已写入终态（`CONFIRMED`/`RELEASED`/`EXPIRED`）后，Redis 失败不允许引发 DB 回滚
2. DB 与 Redis 不一致时，以 DB reservation 状态为最终裁决
3. 所有 Redis 补偿失败必须写 `inventory_deduct_record` 执行日志 + 告警（level=ERROR）

## 3. 幂等裁决

| 操作 | 幂等键组成 | 二次请求处理 |
|------|-----------|-------------|
| `reserve` | 订单创建业务键（由 Order 域生成） | 返回原始 occupyPairs，不重复写 |
| `confirm` | `paymentId`（全局唯一）| 返回原始确认结果，不重复扣 stock |
| `release` | `tradeId` 或 cancel command 业务键 | 返回原始释放结果，不重复回补 |

## 4. 并发竞争裁决（expire vs confirm）

| 竞争场景 | 处理规则 |
|----------|----------|
| confirm 获得行锁，expire 等待 | confirm 成功为 `CONFIRMED`；expire 获锁后发现已是终态，跳过 |
| expire 获得行锁，confirm 等待 | expire 成功为 `EXPIRED`；confirm 获锁后发现已是 `EXPIRED`，返回 conflict |
| 两者同时写入 | 行锁（`SELECT FOR UPDATE`）保证串行化，不存在双写 |

**必须使用 `SELECT FOR UPDATE`（悲观锁）处理所有状态迁移**，禁止用乐观锁（版本号）处理 expire/confirm 竞争，因为 conflict 后的重试代价不对称。

## 5. 支付回调重试裁决

| 场景 | 幂等结果 | 处理 |
|------|---------|------|
| 支付回调第二次到达，reservation 已 `CONFIRMED` | 幂等成功 | 正常返回，不重复发事件 |
| 支付回调第二次到达，reservation 已 `EXPIRED` | conflict | 返回 `INVENTORY_CONFIRM_CONFLICT`，不发 `TRADE_PAID` |
| 支付回调到达时 reservation 不存在 | 系统异常 | 告警 + 人工处理，禁止自动创建新 reservation |
