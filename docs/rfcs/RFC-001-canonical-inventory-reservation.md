# RFC-001：库存语义统一与 Canonical Reservation 架构

> 状态：APPROVED  
> 创建时间：2026-05-25  
> 实施分支：main  
> 配套文档：
> - [语义宪章](../architecture/inventory-semantic-charter.md)
> - [状态矩阵](../architecture/inventory-reservation-status-matrix.md)
> - [失败裁决](../architecture/inventory-failure-arbitration.md)
> - [迁移说明](../architecture/inventory-migration-notes.md)

---

## 1. 背景与动机

当前仓库存在三套并存的库存模型，彼此语义边界模糊，导致业务正确性难以推断：

| 模型 | 当前角色 | 问题 |
|------|----------|------|
| `inventory_reservation` | 旧预留生命周期 | 状态词汇为 RESERVED/COMMITTED，与 V2 deduct 语义错位 |
| `inventory_deduct_record` | V2 执行日志 | 隐含承载了生命周期状态，容易被误当权威 |
| Order 侧 `inventoryPreOccupyIds` / `inventoryReservationId` | 投影引用 | 字段名与语义不一致，"旧名承载新义" |

同时，Order 域支付成功回调（`onPaymentSucceeded`）当前**不执行库存确认**，导致 Redis 准入已扣减、DB 预留状态悬空。

---

## 2. 目标架构

### 2.1 四个 Authority 边界（固定，不允许偏离）

| 组件 | 职责 | 是否权威 |
|------|------|----------|
| `inventory_reservation` | 实时库存预留生命周期 | ✅ 权威 |
| `inventory_stock.total_quantity` | 已确认扣减后剩余可售库存 | ✅ 权威 |
| Redis | 高并发准入控制 | ❌ 非权威（最终以 DB 为准）|
| `inventory_deduct_record` | 执行审计日志 | ❌ 非权威 |

### 2.2 Canonical 生命周期

```
PRE_DEDUCTED → CONFIRMED   支付成功，终态
PRE_DEDUCTED → RELEASED    主动取消，终态
PRE_DEDUCTED → EXPIRED     超时，由 Inventory 调度器驱动，终态
```

所有状态迁移必须遵守：
- `CONFIRMED` 之后禁止任何状态回退，只能走售后/退款
- `EXPIRED` 只能由 Inventory 域内部产生，Order 域禁止写入
- `CONFIRMED`/`RELEASED`/`EXPIRED` 互不覆盖（幂等时返回成功，不重复写入）

### 2.3 Order 投影策略

- Order 域保留库存 projection，仅用于展示和编排分支，不作为真相判断依据。
- 引入 `inventory_projection_version`（1=legacy，2=canonical）。
- version 2 订单使用 `InventoryReservationRef`（shopId/skuId/reservationId）替代旧 `InventoryOccupyPair`。
- `occupyId` 直接复用为 `reservationId`，禁止重新生成第二套引用 ID。

---

## 3. Rollout C（三批次上线策略）

| 批次 | 内容 | Feature Flag 状态 |
|------|------|-------------------|
| Batch-1 | Schema + canonical 代码 + Order 双读双写代码 | `use-canonical-reservation-api=false` |
| Batch-2 | 灰度打开新链路 | `use-canonical-reservation-api=true`（灰度）|
| Batch-3 | version 1 出清后，关闭 legacy API 与旧列双写 | `legacy-stock-api-enabled=false` |

---

## 4. Feature Flags

### Inventory 模块

```yaml
inventory:
  legacy-stock-api-enabled: true         # Batch-3 前保持 true
  reservation:
    canonical-enabled: true
    expiry:
      enabled: true
      fixed-delay: PT1M
```

### Order 模块

```yaml
order:
  inventory:
    use-canonical-reservation-api: false  # Batch-2 打开
    confirm-on-payment-enabled: false     # 与上一个同步打开
    compat-allow-version1-drain: true     # version1 订单出清前保持 true
```

---

## 5. 不变量（Invariants）

1. 同一个 trade 下所有 shop_order 必须使用同一 `inventory_projection_version`。
2. `inventory_deduct_record` 禁止新增 `CONFIRMED` 状态字段。
3. Redis 与 DB 状态不一致时，以 DB 的 reservation 状态为最终裁决。
4. DB 已写入终态后，Redis 补偿失败只允许告警+重试，禁止回滚 DB。
5. 支付成功但 confirm 返回冲突时，禁止发布 `TRADE_PAID`/`ORDER_PAID`，只发布 `INVENTORY_CONFIRM_CONFLICT`。
