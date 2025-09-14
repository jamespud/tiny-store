# Inventory 模块说明 (占位)

## 1. 模块目标
提供总库存(total)、预留库存(reserved) 管理：预留、释放、确认、调整、查询以及异步落库与过期回收。

## 2. 关键概念
- totalQuantity: 数据库权威总量
- reservedQuantity: 已被预留待确认
- available = total - reserved
- reservation: 一次预留行为 (reservationId 唯一)
- operationId: 幂等调用标识（当前未实现，见“API 迁移与幂等”）

## 3. 状态机 (ReservationState)
PENDING -> CONFIRMED / RELEASED / EXPIRED / FAILED

## 4. Redis Key 规范
```
stock:total:{shopId}:{skuId}
stock:total:version:{shopId}:{skuId}
stock:reserved:{shopId}:{skuId}
stock:reservation:state:{reservationId}
stock:reservation:ttl:{reservationId}
stock:op:{operationId} -> reservationId
```

## 5. 幂等策略（规划中）
当前代码尚未实现 operationId 幂等与重复预留保护。后续计划：reserve 接口接受可选 operationId，若重复则返回原 reservationId。

## 6. 事件 (StockEvent.type)
RESERVED / RELEASED / CONFIRMED / TOTAL_ADJUST
字段: type, shopId, skuId, deltaTotal, deltaReserved, reservationId, version, correlationId, ts

## 7. 主要接口 (占位)
- POST /inventory/reserve
- POST /inventory/confirm
- POST /inventory/release
- POST /inventory/adjust
- GET  /inventory/available
- POST /inventory/available/batch

## 8. 过期回收
Scheduler 扫描 PENDING 且 expireAt < now -> 减 reserved -> 标记 EXPIRED -> 事件

## 9. 风险与回滚
- 超卖: Lua 校验 total - reserved >= qty
- 版本冲突: cacheVersion < dbVersion 才更新
- 消息丢失: Kafka acks=all + 重试 + DLQ (待实现)
- 预留遗留: 定时扫描 + TTL
- 负库存检测: 指标 & 报警

## 10. 初始化数据 (占位)
建议编写 SQL 初始化库存行并置 total=期望初值, reserved=0, version=1.

## 11. 后续工作 (未实现)
- 实际持久化实现 (JPA/MyBatis)
- Kafka Producer/Consumer 逻辑
- Redis 快速路径 Lua 扩展 (可用库存批量查询)
- 指标上报与 APM 集成

## 12. API 迁移与弃用说明
旧方法 (将移除):
| 旧方法 | 新方法 | 备注 |
|--------|--------|------|
| deductReserve(shopId, skuId, quantity) | reserve(shopId, skuId, quantity, expireSeconds) | 旧无过期参数，迁移时选择合适 expireSeconds |
| increaseReserve(shopId, skuId, quantity) | reserve(...) | 旧方法只是简单增加预留，语义已统一 |
| increaseTotal(shopId, skuId, quantity) | adjustTotal(shopId, skuId, +delta, reason) | reason 可自定义 |
| decreaseTotal(shopId, skuId, quantity) | adjustTotal(shopId, skuId, -delta, reason) | 需校验 available 不为负 |
| batchQueryAvailable(List<String>) | batchQueryAvailable(List<AvailableQuery>) | 新增结构化 DTO |

以上旧方法已 @Deprecated(forRemoval=true)，未来版本将删除。

## 13. 幂等与后续增强规划
- reserve 幂等: operationId 写入 Redis（op:{operationId}）映射 reservationId
- confirm/release 幂等: reservation 状态机二次调用直接返回成功视图
- adjustTotal 审计: 事件 + journal 表（未实现）

(当前文件为执行计划中的文档占位，不代表最终实现。)
