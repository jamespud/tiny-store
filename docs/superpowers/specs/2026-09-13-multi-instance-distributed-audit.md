# Multi-Instance Distributed Audit — Spec (binding authority)

> 日期：2026-09-13
> 来源：两副本真实栈上的实测审计。完整证据（日志、命令输出、复现步骤、覆盖边界）在
> [`docs/architecture/multi-instance-testing-blockers.md`](../../architecture/multi-instance-testing-blockers.md)。
> 本文件是该审计的**约束化摘要**：实施计划以本文件为绑定权威，计划文本与它冲突时按本文件裁决。

## 1. 目的与范围

把 tiny-store 从"可以在多副本下启动并跑通 happy path"推进到"多副本下 ID、幂等、消息、共享状态、入口与故障转移语义正确"。

**范围内**：order / promotion / inventory / payment / auth / gateway 的分布式正确性与可用性；多实例回归门禁。
**范围外**：PostgreSQL/Redis/Kafka/Nacos 自身的 HA 化；性能调优目标；与多实例无关的功能开发。

## 2. 审计方法（证据纪律）

每条结论都必须能在**两副本真实栈**上复现，且区分四类强度：

1. **量化实测**（如 `200 并发 → 197 成功`、`duplicatePublishes=700/2655`）；
2. **单次实机复现 + 代码定位**（如 DLT 里的 `No argument for named parameter ':couponId'`）；
3. **代码级核对**（如 `TokenVersionFilter` 每次读 Redis、`JwksCacheService` 按 TTL 收敛）；
4. **推断**——**不得**作为验收依据，只能作为待验证假设。

审计中已有一例假设被实测**证伪**并因此改写结论（"quote 阶段泄漏券锁" → 实测 `lockId=null`，券不在 quote 加锁），
实施过程中若出现同类证伪，以实测为准并回写本 spec。

## 3. 阻塞点清单（ID 为稳定标识）

### P0 — 下单链路正确性

| ID | 一句话 | 证据强度 |
|---|---|---|
| C0 | 所有副本共用 `datacenterId=1/workerId=1` 的 Snowflake，并发下单撞唯一约束 → 500；100 并发 5.03%、300 并发 7.49% | 量化实测 |
| C9 | 下单失败后幂等键被两层各自扣住（网关 `COMPLETED` + 订单域锁 600s，且无缓存响应），同键重试 409 | 量化实测 |
| C10 | `TradeController.createTrade` 的 catch-all 把业务冲突也压成 500，客户端分不清"别重试"与"请重试" | 实测 + 代码 |
| C11 | 请求指纹只写不校验：同键换 body 返回 200 + 上一单标识（新单未建） | 量化实测（含端到端 5 分钟窗口） |
| C12 | Kafka 停几分钟 → 下单仍 200，`INVENTORY_RESERVE_DB` 进终态 `FAILED` 无重投 → 订单被自动取消 + 1 件库存永久丢失 | 量化实测 |
| C15 | `findFirstByUserIdAndCouponIdAndUseStatusForUpdate` 缺 `@Param("couponId")` → 用券下单 100% 被取消 | **已修复并验证** |

### P1 — 可靠性与入口

| ID | 一句话 | 证据强度 |
|---|---|---|
| C1 | payment 消费者要 `Acknowledgment` 但无 `ack-mode: manual` → 订单事件 100% 失败后被丢弃 | 量化实测 |
| C2 | `cancelTrade` 与 `PromotionAckConsumer` 抢同一 `trade` 行 → 乐观锁 500（冷启动复现） | 实机复现 |
| C3 | outbox 的 `FOR UPDATE SKIP LOCKED` 写在事务外 → claim 失效，双副本重复投递 700/2655 | 量化实测 + 代码 |
| C16 | 限流身份取自客户端可控 `X-Forwarded-For` → 轮换该头即可 200/200 绕过 | 量化实测 |
| E1 | 副本被 kill 后请求仍打到死实例、无跨实例重试 → 约一半请求失败并持续 20s+ | 量化实测 |

### P2 — 共享状态 / 运维

| ID | 一句话 |
|---|---|
| C4 | OTP 只存签发副本的内存 `@Primary` 适配器 → 跨副本登录约 `(N-1)/N` 概率失败 |
| C5 | auth 审计 `ip` 列 `inet` 绑定用 `setString` → `/otp/send` 500，并掩盖 C4 的真实原因 |
| C6 | 无单实例锁的定时任务中，`OrderTimeoutScheduler` 无 claim 且用随机幂等键（会被重复执行） |
| C8 | `confirmTradeReceipt` 对非 `PENDING_RECEIVE` 子单静默跳过仍返回 200（26 次 E2E 命中 1 次） |
| D1 | `ZIPKIN_ENDPOINT` 为空时仍建 reporter → 每实例持续丢 span |
| D2 | 网关路由 strip 策略与服务控制器不一致 → 统一入口不成立，E2E 只能直连端口 |
| D3 | 有状态层单点；order `100/instance`，3 副本理论连接池总量超过 PG `max_connections` |
| A4 | 8 个应用服务里只有 gateway 有 healthcheck，启动 44–71s 且方差大 |
| C7 | 启动期 Kafka 同组重平衡（瞬态，仅需可观测） |
| C13 | 券在 commit 才预占 → 并发用同券"先全部下单成功"；**最终仅一单胜出（已验证正确）** |
| C14 | `compensationRequired` 置位前的失败会留下 `QUOTED` 报价单，靠 5 分钟定时任务兜底 |

## 4. 绑定不变量（验收口径）

任何修复都不得违反以下不变量；它们同时也是最终 gate 的判据。

```text
ID            : 业务 ID 不得依赖节点状态；并发生成不产生唯一约束冲突
Idempotency   : 同键同 body = 确定性重放；同键不同 body = 409；失败后同键可安全重试
HTTP          : 业务冲突一律 4xx；500 只表示真正的服务端故障；响应不得泄漏 SQL 约束文本
Messaging     : at-least-once + 消费者幂等；正常双副本下 duplicatePublish = 0；
                瞬时 broker 故障不得导致事件永久 FAILED；毒消息有界重试后进 DLT
Shared state  : 跨副本必需的状态必须落在共享存储（Redis/DB），不得落在单 JVM 内存
Gateway       : 全部业务域经由网关可达；不可信来源伪造 XFF 不得绕过限流；
                仅对幂等安全的读方法（GET/HEAD）做跨实例重试
Availability  : 杀掉一个副本不得让读请求失败或长期命中死实例
Resources     : 下单失败不泄漏库存；多店铺部分失败释放已成功店铺；未支付过期归还可售数量
```

## 5. 计划编码的架构决策

这些是实施时必须遵守、不得在任务内重新协商的决定：

1. **业务 ID 改为无节点状态 UUID**（`IdUtil.fastSimpleUUID()`），不引入 Redis worker-id lease——直接消灭该 failure mode。
2. **订单域是 create-trade 的唯一 business idempotency owner**；网关对 order route 关闭 stateful idempotency，只负责传递并校验 `Idempotency-Key` 的存在。
3. **幂等获取必须是原子 Lua**，区分 `ACQUIRED / IN_PROGRESS / REPLAY / FINGERPRINT_CONFLICT`；`SUCCEEDED` 只在事务 `afterCommit` 写入，回滚时删除 `PROCESSING`。
4. **Outbox 采用 claim 协议**（`PROCESSING + claimed_by/claimed_at` + 短事务 `FOR UPDATE SKIP LOCKED`），锁不跨 Kafka publish；必须有 stale lease 回收。
5. **不用全局 scheduler lock 掩盖水平扩展问题**，改用 work claiming；只有天然唯一 leader 的任务才允许全局锁。
6. **不用 sticky session 解决任何共享状态问题**。
7. **mutation 的跨实例自动重试在完成业务幂等 response replay 之前一律禁止**。
8. **库存继续坚持 DB 终态权威 + Redis admission**，不改变权威方向。

## 6. 非目标

* 不把 coupon 预占前移到 quote/下单阶段（C13 的最终一致性已被验证正确，只要求 API 如实表达 `promotionCommitStatus=PENDING`）。
* 不在本计划内做 PostgreSQL/Redis/Kafka HA 或分库分表。
* 不重写所有 controller 去统一 `StripPrefix` 数字（按服务真实 controller contract 配置路由）。
* 不把 `max_connections` 拉高来掩盖连接预算问题。

## 7. 已知覆盖边界（实施时必须保留的诚实标注）

| 未覆盖 | 原因 |
|---|---|
| 开启鉴权后的多副本链路（跨副本签发票据/校验、吊销传播） | 测试栈里取不到 token：auth 无种子凭证，`/api/auth/login/password` 不返回 token，OIDC 授权码流程未在 hermetic 栈搭建 |
| 混合版本 / 滚动升级共存 | 属升级维度，需要另行构建旧版本镜像 |
| 高于 300 VU 的负载 | 审计压到 100/300 两档 |
| Redis 部分故障（时好时坏） | 难以稳定构造；Redis 全挂时订单域 fail-closed(503) |
