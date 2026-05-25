# 库存迁移说明（Inventory Migration Notes）

> 版本：1.0  
> 所属 RFC：[RFC-001](../rfcs/RFC-001-canonical-inventory-reservation.md)

---

## 1. Schema 迁移清单

| 迁移文件 | 模块 | 说明 |
|----------|------|------|
| `V7__canonicalize_inventory_reservation_status.sql` | inventory | 状态词汇迁移 + `confirmed_at` 列 |
| `V12__shop_order_add_inventory_reservation_refs_and_version.sql` | order | 新增 projection 列 |
| `V13__canonicalize_shop_order_inventory_status.sql` | order | 状态词汇迁移 + JSON 回填 |

## 2. 兼容双写策略

| 字段 | version 1 行为 | version 2 行为 | 清理时机 |
|------|---------------|----------------|---------|
| `inventory_pre_occupy_ids_json` | 写入 | 继续写入（兼容） | version 1 订单全部出清后 |
| `inventory_reservation_refs_json` | 不写 | 写入 | - |
| `inventory_projection_version` | 1 | 2 | - |
| `trade.inventory_reservation_id` | 写入 | 停止写入 | version 1 出清后可删列 |

## 3. 状态迁移回填规则

### Inventory `inventory_reservation.status`

```sql
-- RESERVED → PRE_DEDUCTED（历史在途）
UPDATE tinystore_inventory.inventory_reservation
SET status = 'PRE_DEDUCTED'
WHERE status = 'RESERVED';

-- COMMITTED → CONFIRMED（已完成订单）
UPDATE tinystore_inventory.inventory_reservation
SET status = 'CONFIRMED',
    confirmed_at = updated_at
WHERE status = 'COMMITTED';
```

### Order `shop_order.inventory_status`

```sql
-- LOCKED → PRE_DEDUCTED
UPDATE tinystore_order.shop_order
SET inventory_status = 'PRE_DEDUCTED'
WHERE inventory_status = 'LOCKED';

-- DEDUCTED → CONFIRMED
UPDATE tinystore_order.shop_order
SET inventory_status = 'CONFIRMED'
WHERE inventory_status = 'DEDUCTED';
```

## 4. 回滚策略

本轮不依赖 schema 物理回滚，回滚优先使用 feature flags：

1. 关闭 `order.inventory.use-canonical-reservation-api`
2. 保持 `inventory.legacy-stock-api-enabled = true`
3. Canonical 接口保持在线，等待 version 2 订单出清

**禁止在有 version 2 在途订单时做以下操作**：
- 删除 canonical reservation 代码
- 删除 `inventory_reservation_refs_json` 列
- 删除 `InventoryReservationController`

## 5. 清理时机与条件

| 清理项 | 前提条件 |
|--------|---------|
| 删除 `StockController` commit 接口 | Batch-1 上线后立即可下线 |
| 关闭 `legacy-stock-api-enabled` | version 1 订单全部 CLOSED/SUCCESS |
| 删除 `inventory_pre_occupy_ids_json` 列 | legacy API 关闭后，新增 DB migration V14 |
| 删除 `trade.inventory_reservation_id` 列 | version 1 订单全部出清后，新增 DB migration V15 |
| 删除 `InventoryOccupyPair` 主路径代码 | 与 V14 同步 |
| 删除 `StockAppService` | 与 V14 同步 |

## 6. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| V7 迁移脚本运行时表量大，锁超时 | 在低峰期执行，考虑分批 UPDATE（每批 1000 行）|
| version 1 与 version 2 在同一 trade 混用 | `createTrade` 中强制所有 shop_order 同版本，测试覆盖 |
| Redis 准入计数与 DB 长期不一致 | 补偿任务 + 告警，并在监控大盘配置差值告警 |
| `onPaymentSucceeded` confirm 超时 | Feign timeout 设置 3s；超时后 Payment 回调可重试，confirm 幂等保证不重复扣 |
