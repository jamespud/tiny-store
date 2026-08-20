# 库存对账缺口修复实现计划（2026-08-20）

> 本文件与设计文档均为 untracked，不提交（用户要求保留 untrack）。
> 实现已按 TDD 完成并全量验证（见提交历史与测试结果）。

## 提交记录（codex/reconcile-fix）
- `bf13c64` refactor(inventory): paged reconcile scan (Fix 7)
- `0203fca` feat(inventory): authoritative Redis key init on reconcile (Fix 1)
- `62633d4` feat(inventory): atomic dual-field oversell repair (Fix 2)
- `42f5a0c` feat(inventory): reconcile audit-log failure counter (Fix 4)
- `0e6d88b` feat(inventory): single-instance reconcile lock (Fix 6)
- `c8436ef` chore(inventory): remove V1 dead code and empty TODO stubs (Fix 5)
- `7bcf172` test(inventory): scope reconcile-log assertions to test SKU

## 测试证据
- 全模块单元测试：BUILD SUCCESS（reactor 全绿）。
- 全模块集成测试 `clean verify -Pit`：BUILD SUCCESS（inventory/order/product/payment/gateway/auth/account/promotion）。
- 关键覆盖：
  - `InventoryRedisManagerTest`：initStateV2 SETNX 语义、repairOversellV2 原子双字段/版本不匹配。
  - `InventoryReconcileJobTest`：缺失键初始化、版本缺失初始化、组合修复、CAS-skip、审计失败 metric、锁行为、分页。
  - `InventoryReconcileJobIT`：flush 后网关重建含 confirmed（red→green 验证）、对账补建权威状态、并发 CAS 幂等、repair 与业务 preDeduct 并发无丢更新。

## 运行方式
- 单元：`./mvnw test`
- 集成：`./mvnw clean verify -Pit -DskipITs=false -DskipTests -pl '!tests/api,!tests/performance'`
