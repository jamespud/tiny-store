# 库存预留状态矩阵（Inventory Reservation Status Matrix）

> 版本：1.0  
> 所属 RFC：[RFC-001](../rfcs/RFC-001-canonical-inventory-reservation.md)

---

## 1. Canonical 状态迁移矩阵

| 当前状态 | 触发操作 | 目标状态 | 结果 | Redis 动作 | stock 动作 |
|----------|----------|----------|------|-----------|-----------|
| _(无)_ | `reserve` | `PRE_DEDUCTED` | 成功 | 扣减准入计数 | 无 |
| `PRE_DEDUCTED` | `confirm` | `CONFIRMED` | 成功 | 无（准入计数已消费） | `total_quantity -= qty` |
| `PRE_DEDUCTED` | `release` | `RELEASED` | 成功 | 回补准入计数 | 无 |
| `PRE_DEDUCTED` | `expire`（调度器） | `EXPIRED` | 成功 | 回补准入计数 | 无 |
| `CONFIRMED` | `confirm` | `CONFIRMED` | 幂等成功 | 无 | 无 |
| `CONFIRMED` | `release` | ❌ 拒绝 | conflict | 无 | 无（须走售后/退款）|
| `CONFIRMED` | `expire` | ❌ 不可达 | 调度器跳过 | 无 | 无 |
| `RELEASED` | `release` | `RELEASED` | 幂等成功 | 无 | 无 |
| `RELEASED` | `confirm` | ❌ 拒绝 | conflict | 无 | 无 |
| `RELEASED` | `expire` | ❌ 不可达 | 调度器跳过 | 无 | 无 |
| `EXPIRED` | `confirm` | ❌ 拒绝 | conflict | 无 | 无 |
| `EXPIRED` | `release` | `EXPIRED` | 幂等成功 | 无 | 无 |
| `EXPIRED` | `expire` | `EXPIRED` | 幂等成功 | 无 | 无 |

## 2. 禁止的直接迁移（硬性约束）

```
CONFIRMED → RELEASED    ❌  须走售后/退款
CONFIRMED → EXPIRED     ❌  不可能，调度器只扫描 PRE_DEDUCTED
RELEASED  → PRE_DEDUCTED ❌  终态不可逆
EXPIRED   → PRE_DEDUCTED ❌  终态不可逆
```

## 3. Order 侧 InventoryStatus 映射

| Canonical 状态 | Order `InventoryStatus`（version 2）| 历史兼容值（version 1）|
|----------------|-------------------------------------|----------------------|
| `PRE_DEDUCTED` | `PRE_DEDUCTED` | `LOCKED` |
| `CONFIRMED` | `CONFIRMED` | `DEDUCTED` |
| `RELEASED` | `RELEASED` | `RELEASED` |
| `EXPIRED` | `EXPIRED` | _(不存在，历史无此路径)_ |
| _(无预留)_ | `UNLOCKED` | `UNLOCKED` |

## 4. 历史词汇兼容映射（只读，不允许新写入）

| 历史词汇 | Canonical 等价 | 允许写入 |
|----------|---------------|----------|
| `RESERVED` | `PRE_DEDUCTED` | ❌ |
| `COMMITTED` | `CONFIRMED` | ❌ |
| `LOCKED` | `PRE_DEDUCTED` | ❌ |
| `DEDUCTED` | `CONFIRMED` | ❌ |
| `UNLOCKED` | _(初始态)_ | 历史兼容保留 |
