# 库存语义宪章（Inventory Semantic Charter）

> 版本：1.0  
> 所属 RFC：[RFC-001](../rfcs/RFC-001-canonical-inventory-reservation.md)

---

## 1. 词汇表

| 术语 | 定义 | 是否 Canonical |
|------|------|----------------|
| `PRE_DEDUCTED` | Redis 已准入，DB 预留已写入，等待支付确认 | ✅ |
| `CONFIRMED` | 支付成功，库存已最终扣减，不可逆 | ✅ |
| `RELEASED` | 主动取消或超时释放已完成，库存已回补 | ✅ |
| `EXPIRED` | 超时由 Inventory 调度器强制释放 | ✅ |
| `RESERVED` | 历史词汇，等价于 `PRE_DEDUCTED` | ❌ 仅限迁移兼容读 |
| `COMMITTED` | 历史词汇，等价于 `CONFIRMED` | ❌ 仅限迁移兼容读 |
| `LOCKED` | Order 侧投影历史词汇，等价于 `PRE_DEDUCTED` | ❌ 仅限迁移兼容读 |
| `DEDUCTED` | Order 侧投影历史词汇，等价于 `CONFIRMED` | ❌ 仅限迁移兼容读 |
| `UNLOCKED` | Order 侧投影初始态（无预留），不映射到 reservation 状态 | ❌ 仅历史兼容 |

## 2. Authority 边界宪章

### 2.1 `inventory_reservation`（权威）
- **拥有者**：Inventory 域
- **职责**：实时库存预留生命周期的唯一权威
- **禁止行为**：
  - 不允许 Order 域直接写状态
  - 不允许从 `CONFIRMED` 回退到任何状态
  - 不允许同一 `reservationId` 出现两条有效记录

### 2.2 `inventory_stock`（权威）
- **拥有者**：Inventory 域
- **职责**：`total_quantity` 代表已确认扣减后剩余可售库存
- **禁止行为**：
  - `reservedQuantity` 不允许作为 canonical 决策读取依据（仅观测用）
  - 不允许在 RELEASED 时重复回补（需幂等保护）

### 2.3 Redis（非权威）
- **拥有者**：Inventory 域（写）；Order 域通过 inventory RPC 间接触发
- **职责**：高并发准入控制，防止无效 DB 请求
- **禁止行为**：
  - 不允许将 Redis 状态作为最终裁决
  - Redis 失败不允许阻止 DB 写入已成功的终态

### 2.4 `inventory_deduct_record`（非权威）
- **拥有者**：Inventory 域
- **职责**：执行审计日志（reserve 动作、release 动作的执行记录）
- **禁止行为**：
  - 不允许新增 `CONFIRMED` 状态
  - 不允许承载生命周期状态机的裁决逻辑

## 3. Order 域投影宪章

- Order 域的库存投影只用于：展示、编排分支判断、跨服务引用传递
- 任何"库存真相判断"必须调用 Inventory 域
- `inventoryReservationId`（trade 级）在 version 2 后停止写入
- `inventory_projection_version = 2` 订单：使用 `InventoryReservationRef`（shopId/skuId/reservationId）
- `inventory_projection_version = 1` 订单：使用旧 `InventoryOccupyPair`，不补做 confirm

## 4. 违规处理规则

| 违规场景 | 处理方式 |
|----------|----------|
| Order 域写入 `EXPIRED` | 代码审查拒绝，测试失败 |
| `inventory_deduct_record` 写入 `CONFIRMED` | 代码审查拒绝 |
| 支付成功不做 confirm 就发 `TRADE_PAID` | 运行时断言/测试覆盖 |
| Redis 失败回滚 DB 已写终态 | 代码审查拒绝，架构评审 |
