# 库存对账缺口修复设计（2026-08-20）

> 目标：修复对账逻辑在高并发/容错/宕机场景下的缺口，并删除 V1 死代码。
> 状态：设计已批准并已按 TDD 实现完毕（见实现计划）。本文件与实现计划均为 untracked，不提交。

## 背景缺口（来自 2026-08-19 分析）
- **Gap A**：Redis 键丢失后 `ensureTotalKeyInitialized`/`addTotal` 重建 total 只取 `dbTotal`，漏掉 confirmed → 对账看到 `totalTooLow`（只告警）且 `deductedTooLow`（会主动 INCRBY）→ 永久 over-reject。
- **Gap B**：null Redis key 被当作 in-sync，对账对 Redis flush 完全无感。
- **Gap C**：漏卖方向只告警（保守、正确，保留）。
- **Gap D**：双字段修复分两次 CAS，可能部分修复、跨多轮收敛。
- **Gap E**：对账审计日志写失败被吞掉，不可观测。
- **Gap F**：promotion `ReconciliationTask` 空 TODO 桩。
- 另有 V1 死代码（方法/常量/脚本/死 Lua）与无 leader 选举、全表扫描问题。

## 修复项
1. **Fix 1（Gap A+B）**：权威 Redis 键初始化
   - 对账发现 V2 键缺失时用原子 Lua（SETNX 不覆盖）按权威目标补建 total/deducted/version。
   - 网关初始化路径改为 `dbTotal + confirmed`，与 `ReconciliationSnapshot.getTargetTotal()` 对齐。
2. **Fix 2（Gap D）**：`repairOversell` 单 Lua 原子双字段修复（一次 CAS 同时 DECRBY total + INCRBY deducted）。
3. **Fix 3（Gap C）**：漏卖方向维持只告警；缺失键场景由 Fix 1 覆盖。
4. **Fix 4（Gap E）**：新增 `reconcile.log.failed.total` counter。
5. **Fix 5（Gap F + 死代码）**：删 V1 方法/常量/脚本、死 Lua（reserve.lua、batch_available.lua）、空 TODO 任务。
6. **Fix 6**：对账单实例化（Redis `SET NX PX` 锁）。
7. **Fix 7**：对账分页扫描（`Pageable`）。

## 关键不变量（保持）
- DB reservation 状态为最终裁决；Redis 失败不回滚 DB 终态。
- 所有写 total/deducted 的操作原子 bump version；修复只用增量 + CAS，绝不 SET 覆盖。
- 超卖方向自动修复；漏卖方向只告警。
