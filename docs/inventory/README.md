# Inventory 模块说明

## 1. 模块目标
提供总库存(totalQuantity)、预留库存(reservedQuantity) 管理：预留(Reserve)、确认(Confirm)、释放(Release)、过期(Expire)、调整总量(AdjustTotal)、可用库存查询(Available)；实现最终一致 + 乐观锁防超卖；支持幂等 operationId（Phase1 已实现）。

## 2. 领域模型
- StockAggregate: (shopId, skuId) 聚合根，字段：totalQuantity, reservedQuantity, version；派生 available = totalQuantity - reservedQuantity
- Reservation: 预留记录，字段：reservationId, shopId, skuId, quantity, state, expireAt, operationId, version
- 不变量：total>=0, reserved>=0, reserved<=total；reservation.quantity>0；expireAt>createdAt

## 3. Reservation 状态机
```
        +---------+
        | PENDING |----confirm---->+-----------+
        +---------+                | CONFIRMED |
            | release              +-----------+
            v                            ^
        +---------+                      |
        | RELEASED|<--(幂等再次)---------+
        +---------+
            |
         expire
            v
        +---------+
        | EXPIRED |
        +---------+
```
非法迁移直接抛 INV_RESERVATION_STATE_INVALID；重复调用允许幂等返回。

## 4. 可用库存统一
available 只允许通过 StockAggregate.getAvailable() 计算，禁止散落 total - reserved 计算，代码扫描守护。

## 5. Redis Key
```
stock:reservation:ttl:{reservationId}
stock:op:{operationId} -> reservationId
```
(Phase1 未缓存总量与预留分量；Phase2 引入 Lua 原子扣减与批量查询缓存结构)

## 6. 幂等策略
- reserve(operationId): 若 op 已存在直接返回原 reservation 结果
- confirm/release/expire：基于状态机幂等
- adjustTotal: 纯增量，无 operationId，调用方自行幂等控制

## 7. 事件模型 (StockEvent)
字段: eventId, type(RESERVED|CONFIRMED|RELEASED|EXPIRED|TOTAL_ADJUST), shopId, skuId, reservationId?, deltaTotal?, deltaReserved?, quantity?, version, correlationId?, occurredAt
Phase1: 直接日志输出；Phase2: Outbox + Kafka

## 8. 接口列表
| 动作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 预留 | POST | /api/inventory/reservations | body: shopId, skuId, quantity, expireSeconds, operationId? |
| 确认 | POST | /api/inventory/reservations/{id}/confirm |  |
| 释放 | POST | /api/inventory/reservations/{id}/release | body: reason |
| 调��� | POST | /api/inventory/stock/adjust | body: shopId, skuId, delta, reason |
| 单查 | GET  | /api/inventory/stock/available?shopId=&skuId= |  |
| 批查 | POST | /api/inventory/stock/available/batch | body: items[] |

## 9. 错误码
| 码 | 场景 |
|----|------|
| INV_STOCK_NOT_FOUND | 库存不存在 |
| INV_AVAILABLE_NOT_ENOUGH | 可用不足 |
| INV_RESERVATION_NOT_FOUND | 预留不存在 |
| INV_RESERVATION_STATE_INVALID | 状态非法迁移 |
| INV_IDEMPOTENT_REPLAY | 幂等重放（当前直接返回 OK 未单独抛出） |
| INV_ADJUST_ILLEGAL | 调整导致负可用/负总量 |
| INV_ARG_INVALID | 参数不合法 |

## 10. 过期回收
定时任务每 5s 扫描 PENDING 且 expireAt < now(批量 200) -> expire -> 发布 EXPIRED 事件。
冲突重试 3 次，失败记录日志。不会阻塞主流程。

## 11. 一致性策略
Phase1：DB 为权威 + 乐观锁；Redis 仅幂等映射。失败重试 3 次。
Phase2：引入 Lua 原子扣减 + Outbox 事务事件 + 缓存 pipeline。
Phase3：热点分片 Key + Sharding。

## 12. 弃用 API 迁移
| 旧方法 | 新方法 | 状态 |
|--------|--------|------|
| deductReserve | reserve | @Deprecated(forRemoval=true) |
| increaseReserve | reserve | 同上 |
| increaseTotal | adjustTotal(delta>0) | 同上 |
| decreaseTotal | adjustTotal(delta<0) | 同上 |
| confirmReservation | confirm | 已适配 |
| releaseReservation | release | 已适配 |
| available | available | 已适配 |

预计下一个 minor 版本删除旧接口。

## 13. 指标 & 监控 (规划)
Counter: reserve.success/fail, reservation.expire.count
Timer: reserve.latency
Gauge: stock.available

## 14. DDL
见 SCHEMA.md 与 schema-inventory.sql

## 15. 测试策略 (Phase1)
- 领域单测：聚合/状态机
- 服务单测：幂等 / 过期逻辑（内存仓储 stub）
- 控制器：方法调用与参数校验 (Mock Service)

## 16. 当前阶段
Phase1 已实现：聚合 + 仓储 + 幂等 + 定时过期 + 事件占位 + REST API。

## 17. 后续工作
Phase2: Outbox, Kafka 真实投递, Lua 原子扣减, 批量缓存
Phase3: 分桶 Key, Sharding, 指标完善, 审计 Journal

---
(本 README 已替换早期占位内容)
