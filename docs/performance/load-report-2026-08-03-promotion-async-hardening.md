# 加固验证报告 (2026-08-03, F-1..F-5 + outbox 吞吐)

> 环境：单机 docker-compose-test 栈（gateway :8080 + order :28080 + inventory :13000 + promotion :1200 + PG/Redis/Kafka），16 核 / 29G
> 验证对象：`feature/promotion-async-hardening`（flag 开启 `ORDER_PROMOTION_COMMIT_ASYNC_ENABLED=true`）
> 对比基线：[2026-08-02-promotion-async](./load-report-2026-08-02-promotion-async.md)（异步化后、加固前）

## 1. 本次改动（8 任务）

| 项 | 改动 |
|----|------|
| F-1 | V15 backfill：历史 PENDING 未关闭订单 → COMMITTED（flag 开启瞬间不批量误取消） |
| F-2 | TradeEntity @Version 乐观锁；门控异常语义（PENDING 可重试 / TRADE_TERMINAL 终态） |
| F-3 | 已关闭订单收 COMMITTED 回执 → 补偿 promotion release（券不泄漏） |
| F-4 | 回执改为事务提交后发送（afterCommit） |
| F-5 | order consumer DLT（镜像 promotion：毒消息立即 DLT、业务异常重试后 DLT） |
| outbox | batch 1000→5000、poll 5s→1s、SKIP LOCKED 分片、批量标记（一次 UPDATE）、@Transactional 修复、orTimeout 防卡死、独立调度线程池、取消限批 200、超时 30s→60s 可配置、V16 索引 |

## 2. E2E 功能验证（compose-test 实栈）

| 场景 | 结果 |
|------|------|
| 下单 → PENDING → COMMITTED | ✓ 6s（异步链路 6s 内闭环） |
| 支付门控 | ✓ 门控语义区分（IT 覆盖 PENDING/FAILED/CLOSED/COMMITTED） |
| 毒消息 → DLT | ✓ `tinystore.promotion.general.DLT` 收到（此前无 DLT 落点） |
| 券补偿 | ✓ 单测（closed trade 收 COMMITTED 回执 → release 调用） |
| V15/V16 迁移 | ✓ 全新卷执行成功 |

## 3. outbox 发布器吞吐（关键对比）

| 指标 | 加固前 (08-02) | 加固后 (08-03) | 提升 |
|------|---------------|---------------|------|
| 空载发布速率 | ~20 条/s | **4457-5000 条/s** | **+220 倍** |
| 发布批次 | 100/5s 轮询 | 5000/1s 轮询 | — |
| 批量标记 | 逐条 SELECT+UPDATE | 一次 UPDATE（@Transactional 修复后生效） | — |
| 多实例安全 | 互抢同批 | SKIP LOCKED 分片 | — |
| 调度饿死 | 单线程调度器被取消任务独占（实测停摆 20 分钟） | outbox 独立线程池 | 消除 |
| 取消级联 | 30s 超时无限制（风暴） | 60s + 限批 200/轮 | 缓解 |

## 4. 长时压测（VUS=2000，3 分钟，独立 SKU）

- **压测期间**：积压增长（产生 ~6500 事件/s > 发布器压测期 ~413-1500/s）——发布器受 **DB 连接争抢**（Hikari 100 被下单事务占满，发布查询排队）与单机资源限制
- **压测停止后自愈**：积压 **5000/s 消化**（24 万 → 9 万 / 30s）——系统恢复全速，无永久积压
- **取消级联**：限批 + 60s 阈值生效（不再风暴）
- **k6 RPS**：与加固前持平（≥1400 天花板维持）

## 5. 回归

- 全模块单测：order 119 / promotion 29 / library 11 全绿
- 核心矩阵（C=500）：超卖 ✓ / 幂等 ✓（1.7s）/ confirm ✓
- 幂等测试首轮失败：k6 遗留 Kafka 积压 84 万 FIFO 排队（inventory consumer 落后）→ 重置 offset 后通过——测试污染，非功能回归（与 08-02 报告 §B 同类）

## 6. 遗留限制（单机资源上限，非缺陷）

1. **压测持续高压下发布器受 DB 连接争抢**：Hikari 100 被下单占满时发布查询排队（~413-1500/s）。后续可：Hikari 扩容或发布器连接预留/独立数据源
2. **Kafka 消费端 FIFO**：consumer（inventory/promotion）落后时测试/新事件排队——压测前后需清理或重置（压测方法论问题）
3. **发布速率 vs 产生速率**：单机上限 ~5000/s 发布 vs 1400 RPS 下单 × 4.5 事件 ≈ 6500/s——极限压测下短期积压不可避免，靠停止后自愈收敛（当前设计语义：最终一致 + 60s 超时兜底）

## 7. 结论

- **F-1..F-5 全部落地并验证**：flag 开启前置条件满足（backfill、乐观锁、券补偿、afterCommit、DLT）
- **outbox 吞吐提升 220 倍**（20 → 5000 条/s 空载），批量标记/分片/调度隔离/索引全部生效
- 长时压测暴露的积压为**单机资源上限**（DB 连接争抢 + Kafka 消费 FIFO），系统自愈收敛
- 修复过程中发现的 3 个实现级 bug 均已修复并提交（@Modifying 无事务、batch-size 被 yml 遮蔽、allOf 卡死无异常观察）
