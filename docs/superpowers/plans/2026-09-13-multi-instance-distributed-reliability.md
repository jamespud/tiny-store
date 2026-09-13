# tiny-store Distributed Multi-Instance Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 将 tiny-store 从“可以运行多副本”推进到“多副本下具备正确的 ID、幂等、消息可靠性、共享状态、统一入口与故障转移语义”，并让现有多实例测试全部成为长期 regression gate。

**Architecture:** 不通过全局锁或单实例化掩盖分布式问题。订单/Outbox/Scheduler 使用原子 claim、CAS、共享状态和消费端幂等；Gateway 只承担路由、安全和安全可重试职责；库存继续坚持 DB 终态权威 + Redis admission。所有修改按最小可验证增量提交。

**Tech Stack:** Java / Spring Boot / Spring Cloud Gateway / Nacos / Redis / PostgreSQL / Kafka / Spring Kafka / JPA / Flyway / Docker Compose / k6 / Maven.

**Spec:** `docs/superpowers/specs/2026-09-13-multi-instance-distributed-audit.md`

## Global Constraints

* 保留现有单实例 `make e2e` 行为。
* `make e2e-multi` 必须持续全绿。
* 所有分布式修复必须有“两个实际 JVM 副本”的回归测试，不能只靠 Mockito 单测。
* 不通过 sticky session 解决任何共享状态问题。
* 不以全局 scheduler lock 代替可水平扩展的 work claiming，除非任务天然只能有唯一 leader。
* Outbox/消息系统只承诺 at-least-once；消费者必须幂等。
* HTTP 200 只能表达接口定义中的成功语义，业务冲突不得伪装成 500。
* mutation 不在没有完整业务幂等 response replay 前进行跨实例自动重试。
* C15 已修复的 `@Param("couponId")` 必须作为独立提交先落库。
* A3 Flyway 并发 migration 不再作为 correctness blocker；独立 migration Job 留作 deployment hardening。

---

# Plan decomposition

实施拆成五个子计划，顺序固定：

1. `2026-09-13-order-distributed-correctness.md`

   * C15、C0、C9、C10、C11、C2、C6、C8
2. `2026-09-13-outbox-kafka-reliability.md`

   * C3、C1、C12
3. `2026-09-13-auth-distributed-state.md`

   * C5、C4
4. `2026-09-13-gateway-routing-resilience.md`

   * D2、E1、C16
5. `2026-09-13-multi-instance-operational-hardening.md`

   * A4、D1、D3、C7、C14、C13 API contract

前三个属于 correctness；第四个属于 availability/security；第五个属于 hardening。

---

# Sub-plan 1 — Order distributed correctness

## Task 1: 提交 C15 已验证 hotfix

**Files**

* Modify: `tinystore-domain-promotion/src/main/java/com/github/spud/tinystore/promotion/infrastructure/persistence/jpa/repository/JpaUserCouponRepository.java`
* Test: promotion repository / checkout integration tests
* Test: existing multi-instance coupon arbitration probe

**Produces**

* `findFirstByUserIdAndCouponIdAndUseStatusForUpdate(...)` 显式绑定所有 named parameters。

* 为同一个 repository 中 `markAsUsed(...)` 同样补齐显式 `@Param`，消除“依赖编译参数名碰巧一致”。

* [ ] 写/保留真实数据库集成测试：创建可用 user coupon，调用 pessimistic query，必须成功返回。

* [ ] 在未修版本运行测试，确认出现 named parameter binding failure。

* [ ] 给所有 named parameter 增加显式 `@Param`。

* [ ] 运行 promotion 模块测试以及原 C13 五单抢一券实验。

* [ ] 确认最终结果仍是 `COMMITTED ×1 + losers released/closed`。

* [ ] 独立提交：

```bash
git commit -m "fix(promotion): bind coupon query parameters explicitly"
```

---

## Task 2: C0 — 移除跨节点 Snowflake 身份依赖

**Files**

* Modify: `tinystore-domain-order/.../domain/service/TradeIdGenerator.java`
* Test: `TradeIdGeneratorTest.java`
* Test: `tests/performance/.../InventoryNoOversellConsistencyIT.java`
* Test: existing `consistency-multi` ID collision probe

**Decision**

当前 trade/order/paymentIntent ID 都是 opaque `String`，现有代码甚至已经在 Snowflake clock rollback 时使用 `IdUtil.fastSimpleUUID()`。

因此本轮不新增 Redis worker-id lease，不再维护 node identity，直接把三个业务 ID 统一改为无节点状态 UUID：

```java
public static String generateTradeId() {
    return IdUtil.fastSimpleUUID();
}

public static String generateOrderId() {
    return IdUtil.fastSimpleUUID();
}

public static String generatePaymentIntentId() {
    return IdUtil.fastSimpleUUID();
}
```

这是 YAGNI 方案：消灭整个 worker-id failure mode，而不是把它转移到 Redis lease。

* [ ] 写并发测试：8 个 generator threads × 50,000 IDs，断言集合大小等于 400,000。

* [ ] 搜索所有 ID 数值解析依赖：

```bash
grep -R "parseLong.*tradeId\|parseLong.*orderId\|parseLong.*paymentIntentId" -n .
```

期望：无业务 ID numeric parsing。

* [ ] 将 generator 改为无节点 UUID。

* [ ] 运行 order 模块单元/集成测试。

* [ ] 启动两个 order 副本运行：

```bash
make consistency-multi
make load-multi VUS_LEVELS="100 300" DURATION=30s
```

**Acceptance**

* 200 concurrent create：200/200 HTTP success（排除其他业务拒绝）。

* unique constraint collision count = 0。

* k6 中由 Snowflake collision 导致的 5xx = 0。

* [ ] 提交：

```bash
git commit -m "fix(order): make business IDs node-independent"
```

---

## Task 3: C10 — 恢复正确 HTTP error semantics

**Files**

* Modify: `tinystore-domain-order/.../interfaces/rest/TradeController.java`
* Verify: existing `GlobalExceptionHandler`
* Test: `TradeControllerTest` / integration endpoint test

**Change**

`createTrade()` 不再吞掉 `DomainConflictException`：

```java
} catch (IdempotencyServiceUnavailableException e) {
    throw e;
} catch (DomainConflictException e) {
    throw e;
} catch (Exception e) {
    log.error("Create trade failed", e);
    return ResponseEntity.status(500)
        .body(OrderHttpResponse.fail(500, "Internal server error"));
}
```

数据库唯一冲突如果代表用户可解释冲突，则在 application/domain 层转换成 `DomainConflictException`，而不是将数据库异常文本直接暴露给 HTTP。

* [ ] 添加库存不足集成测试，预期 409。

* [ ] 添加幂等冲突集成测试，预期 409。

* [ ] 添加未知 runtime exception 测试，预期 500。

* [ ] 确认响应不再包含 PostgreSQL constraint/internal SQL 文本。

* [ ] 运行 order tests + `make chain-multi`。

* [ ] 提交：

```bash
git commit -m "fix(order): preserve domain conflict HTTP semantics"
```

---

## Task 4: C9 + C11 — 重构订单幂等协议

**Files**

* Modify: `tinystore-domain-order/.../infrastructure/idempotency/IdempotencyService.java`
* Modify: `TradeApplicationService.java`
* Modify: `tinystore-domain-gateway/src/main/resources/gateway-routes.yml`
* Create: `TradeRequestFingerprint.java`
* Test: `IdempotencyServiceIT.java`
* Test: `TradeApplicationService...Test.java`
* Test: `docker/order-retry-probe.sh`

**Architecture decision**

订单域成为 create-trade 的唯一 business idempotency owner。

Gateway 对 `order-service`：

```yaml
idempotency:
  enabled: false
```

Gateway 仍然传递并要求业务 API 自己要求的 `Idempotency-Key`，但不维护第二套 `PROCESSING/COMPLETED` 生命周期。

订单 Redis record：

```text
idempotency:trade:create:<key>

fingerprint=<sha256>
state=PROCESSING|SUCCEEDED
response=<json>
```

### Atomic acquire contract

新增：

```java
AcquireResult acquire(
    String scope,
    String key,
    String fingerprint
);
```

返回：

```java
ACQUIRED
IN_PROGRESS
REPLAY
FINGERPRINT_CONFLICT
```

Redis Lua 必须原子完成：

```text
不存在：
  写 fingerprint + PROCESSING + TTL
  -> ACQUIRED

存在且 fingerprint 不同：
  -> FINGERPRINT_CONFLICT

存在且 fingerprint 相同 + SUCCEEDED：
  -> REPLAY

存在且 fingerprint 相同 + PROCESSING：
  -> IN_PROGRESS
```

### Request fingerprint

必须在 server-generated tradeId 生成前计算。

Fingerprint 输入：

```text
buyerId
addressId
normalized orderLines
platformCouponCodes
shopCouponCodesByShop
client-supplied tradeId（仅当客户端真的传入）
```

不得包含：

```text
server-generated tradeId
traceId
request timestamp
```

所有 list/map 在 hash 前排序，使用 SHA-256。

### Transaction semantics

首次请求：

```text
acquire PROCESSING
→ business transaction
→ COMMIT
→ afterCommit 写 SUCCEEDED + response
```

rollback：

```text
afterCompletion(ROLLED_BACK)
→ 删除 PROCESSING key
```

不要在事务 commit 之前缓存 SUCCESS response。

* [ ] 写 fingerprint 单测：相同业务 body 顺序变化 hash 相同。

* [ ] 写 fingerprint 单测：quantity/address/coupon 改变 hash 必须不同。

* [ ] 写 acquire Lua IT：same key/same fingerprint。

* [ ] 写 acquire Lua IT：same key/different fingerprint → conflict。

* [ ] 写 rollback IT：DB transaction rollback 后同 key 可以重新 acquire。

* [ ] 写 commit replay IT：commit 后同 key 返回原 response。

* [ ] 实现 TransactionSynchronization `afterCommit` / rollback cleanup。

* [ ] 禁用 order route 的 Gateway stateful idempotency。

* [ ] 运行：

```bash
make retry-multi
```

**Acceptance**

Check 1：

```text
forced failure → retry SAME key SAME body
expected: request reaches Order and can execute again
```

Check 2：

```text
same key + changed body
expected: 409
DB: second trade absent
```

Check 3：

```text
same key + same successful body
expected: original tradeId/paymentIntentId replayed
```

* [ ] 提交：

```bash
git commit -m "fix(order): make idempotency transactional and fingerprint-aware"
```

---

# Sub-plan 2 — Outbox and Kafka reliability

## Task 5: C3 — 实现真正的 outbox claim protocol

**Files**

* Modify: order `OutboxEventEntity.java`
* Modify: `OutboxEventJpaRepository.java`
* Modify: `OutboxEventService.java`
* Modify: `OutboxEventPublisherScheduler.java`
* Add Flyway migration under order DB migrations
* Mirror equivalent changes in auth outbox publishers
* Test: order outbox integration tests
* Test: existing multi-instance duplicate-publish probe

**Schema**

增加：

```sql
claimed_by VARCHAR(128),
claimed_at TIMESTAMP,
```

允许状态：

```text
PENDING
PROCESSING
PUBLISHED
FAILED
```

### Claim

在一个短事务中：

```java
@Transactional
public List<OutboxEventEntity> claimPendingEvents(
    int limit,
    String instanceId
) {
    var events = repository.findPendingEventsForUpdate(limit);
    events.forEach(e -> {
        e.setStatus("PROCESSING");
        e.setClaimedBy(instanceId);
        e.setClaimedAt(LocalDateTime.now());
    });
    repository.saveAllAndFlush(events);
    return events;
}
```

`findPendingEventsForUpdate` 保留：

```sql
FOR UPDATE SKIP LOCKED
```

锁的目的只是保证 claim 原子化，不跨 Kafka publish 持锁。

### Publish

事务提交后发 Kafka。

成功：

```text
PROCESSING owned-by-X
→ PUBLISHED
```

失败：

```text
PROCESSING owned-by-X
→ PENDING / FAILED
retryCount++
lastError=...
```

### Stale lease recovery

增加：

```text
PROCESSING
claimed_at < now - claimTimeout
→ PENDING
```

解决：

```text
claim 成功
→ JVM crash
→ 永远 PROCESSING
```

* [ ] 写两个 publisher 并发 claim 同 100 条 event 的 IT。

* [ ] 断言 eventId intersection = empty。

* [ ] 写 publisher crash/stale claim reclaim IT。

* [ ] 实现 order outbox。

* [ ] 实现 auth 两套 outbox 相同语义。

* [ ] 运行现有 probe：

```text
duplicatePublishes = 0
```

在无 crash 的正常双副本情况下必须为 0。

* [ ] 保留 consumer `eventId` 幂等，因为 Kafka ACK 后、DB 标 PUBLISHED 前 crash 仍允许合法 duplicate。

* [ ] 提交：

```bash
git commit -m "fix(outbox): claim events atomically across replicas"
```

---

## Task 6: C1 — 修复 Payment Kafka consumer

**Files**

* Modify: `tinystore-domain-payment/src/main/resources/application.yml`
* Modify: payment order-event consumer
* Create/Modify: payment Kafka consumer configuration
* Test: payment Kafka integration tests

**Listener**

配置：

```yaml
spring:
  kafka:
    listener:
      ack-mode: manual
```

规则：

```text
success → ack.acknowledge()

failure → throw
```

禁止：

```java
catch (Exception e) {
    log.error(...);
    // swallow
}
```

配置：

```text
DefaultErrorHandler
FixedBackOff
DeadLetterPublishingRecoverer
```

例如：

```text
1s × 5 retries
then DLT
```

* [ ] 正常 `PAYMENT_INTENT_CREATED` 测试：生成 1 payment order，ack。

* [ ] duplicate event 测试：最终只有 1 payment order。

* [ ] poison JSON 测试：重试耗尽进入 DLT。

* [ ] transient application error 测试：未成功前不能 ack。

* [ ] 双 payment 副本测试。

* [ ] 完整跑 C3 duplicate probe 后再启用此修复，禁止单独把 C1 合并到仍有 C3 的分支。

* [ ] 提交：

```bash
git commit -m "fix(payment): enforce manual ack and bounded Kafka recovery"
```

---

## Task 7: C12 — 修复关键 Outbox 的故障恢复窗口

**Files**

* Modify: order outbox retry/backoff model
* Modify: producer properties
* Modify: inventory uncommit cleanup configuration
* Test: `OutboxPublisherKafkaDownChaosIT`
* Test/Create: orphan pre-deduct recovery integration test

**Important correction**

当前系统不是已证明“永久丢库存”。

Redis 已有 `InventoryUncommitV2CleanupTask`，默认 30 分钟会清除孤立 uncommit。

因此本任务验收的是：

```text
Kafka 暂停几分钟
不能造成 outbox 永久 FAILED；
不能让 orphan pre-deduct 等满 30 分钟才能恢复。
```

### Outbox transport retry

对 Kafka transport failure：

```text
PENDING
→ PROCESSING
→ send failure
→ PENDING with nextAttemptAt
```

使用 exponential/backoff，不因为 3 次 broker connection failure 进入永久 FAILED。

只有：

```text
不可恢复 payload/validation failure
或超过明确 retention policy
```

才进入 FAILED。

保留 `/internal/outbox/retry/{eventId}` 作为人工恢复入口。

### Producer fail-fast

降低 producer 在 broker down 时单轮 poll 被卡住的时间：

```yaml
max.block.ms: 3000
```

并给 delivery timeout 设置明确上限。

### Inventory safety net

将：

```java
DEFAULT_TIMEOUT_MS = 1800000L
```

改为 property，例如：

```yaml
inventory:
  uncommit:
    timeout: PT30M
```

并建立配置不变量：

```text
uncommit timeout > max legitimate reservation TTL
```

另增加一条 orphan-specific reconciliation：

```text
uncommit member age > orphanCheckDelay
AND DB reservation does not exist
→ rollback Redis admission
```

不要简单缩短所有 uncommit TTL，否则可能误释放合法预约。

* [ ] Kafka down 3–5 分钟 chaos test。

* [ ] 下单 HTTP 200 后恢复 Kafka。

* [ ] 断言 `INVENTORY_RESERVE_DB` 最终 PUBLISHED。

* [ ] 断言 DB reservation 最终存在或订单关闭后 Redis admission 被释放。

* [ ] 断言最终“还能卖出的数量”回到业务预期。

* [ ] 追加旧行为测试：合法 PRE_DEDUCTED 不得被 orphan cleaner 提前释放。

* [ ] 提交：

```bash
git commit -m "fix(order): recover critical outbox events after broker outages"
```

---

# Sub-plan 3 — Auth distributed state

## Task 8: C5 — 先修 audit inet binding

**Files**

* Modify: `tinystore-domain-auth/.../infrastructure/audit/JdbcAuditLogAdapter.java`
* Test: auth audit integration test

绑定 PostgreSQL `inet`：

```java
ps.setObject(index, event.ip(), Types.OTHER);
```

或在 SQL 中显式 cast：

```sql
CAST(:ip AS inet)
```

选择一种并固定。

* [ ] 写真实 PostgreSQL integration test。

* [ ] `/otp/send` 不得因 audit insert 返回 500。

* [ ] `/otp/verify` 的业务错误必须能暴露真实 OTP 错误，而不是 audit SQL exception。

* [ ] 提交：

```bash
git commit -m "fix(auth): bind audit IP as PostgreSQL inet"
```

---

## Task 9: C4 — OTP 改成 Redis 原子 verify-and-consume

**Files**

* Modify: `OtpRepositoryPort`
* Replace: `RedisOtpRepositoryPort`
* Restrict: `InMemoryOtpRepositoryAdapter`
* Modify: OTP application service
* Create: Redis Lua script
* Test: auth multi-instance OTP integration tests

不要复制现有：

```text
findLatest
verify
markUsed
```

三阶段协议。

新增 domain port：

```java
OtpConsumeResult verifyAndConsume(
    String phone,
    String code
);
```

Redis Lua 原子完成：

```text
不存在 → NOT_FOUND
过期 → EXPIRED + remove
code mismatch → MISMATCH
正确且 unused → consume atomically → SUCCESS
```

`InMemoryOtpRepositoryAdapter` 仅允许：

```text
local/dev profile
```

生产/compose multi 必须使用 Redis。

* [ ] auth-A 发 OTP，auth-B 验证成功。

* [ ] auth-A 与 auth-B 同时验证同一个正确 OTP。

* [ ] 断言 exactly one SUCCESS。

* [ ] 再次使用必须失败。

* [ ] timeout 后验证失败。

* [ ] 运行 audit + OTP 全链路。

* [ ] 提交：

```bash
git commit -m "fix(auth): consume OTP atomically in Redis"
```

---

# Sub-plan 4 — Gateway routing and resilience

## Task 10: D2 — 统一 Gateway route contract

**Files**

* Modify: `tinystore-domain-gateway/src/main/resources/gateway-routes.yml`
* Modify equivalent k8s ConfigMap route config
* Test: gateway route integration tests
* Modify multi E2E：核心业务不再直连 service port

**Rule**

不为“统一 StripPrefix 数字”而重写所有 controller。

按服务实际 controller contract 设置路由。

例如当前：

```text
order controller: /order/trades
external: /api/order/**
→ StripPrefix=1

inventory controller: /api/inventory/...
external: /api/inventory/**
→ 不 StripPrefix

product direct API: /api/products/...
external: /api/products/**
→ 不 StripPrefix
```

同时补齐 `/api/skus/**` 的产品查询 route。

* [ ] 为每个业务 domain 定义至少一个 gateway smoke endpoint。

* [ ] gateway route test 覆盖全部 8 services。

* [ ] `MallE2EIT` 等黑盒测试优先只走 gateway。

* [ ] 只有专门的 replica-level test 才允许直接 service port。

* [ ] `make e2e-multi` 全绿。

* [ ] 提交：

```bash
git commit -m "fix(gateway): align public routes with service contracts"
```

---

## Task 11: E1 — 实现安全的 read failover

**Files**

* Modify: gateway LoadBalancer/cache properties
* Modify gateway route retry policy
* Test: `docker/failover-probe.py`
* Test: `make resilience-multi`

第一阶段只对：

```text
GET
HEAD
```

允许跨实例 retry。

显式设置 LoadBalancer cache TTL，例如：

```yaml
spring:
  cloud:
    loadbalancer:
      cache:
        ttl: 2s
```

Gateway retry：

```text
max retries: 1
next instance: true
retry exceptions:
  ConnectException
  ConnectTimeoutException
  connection reset before response
methods:
  GET
  HEAD
```

禁止：

```text
POST/PUT/PATCH 自动 retry
```

直到 Task 4 的 business idempotency 完成并单独证明 mutation retry safe。

* [ ] failover probe baseline 应失败。

* [ ] 杀掉一个 order replica。

* [ ] GET probe 必须 0 个 000/500。

* [ ] p99 failover latency 应有明确上限。

* [ ] Nacos 剔除后不得继续命中 dead instance 超过 SLA。

* [ ] 提交：

```bash
git commit -m "fix(gateway): fail over safe reads across replicas"
```

---

## Task 12: C16 — 建立可信代理边界

**Files**

* Create: gateway `ClientIpResolver`
* Modify: `IpRateLimiterFilter.java`
* Add gateway properties
* Test: gateway rate limiter integration tests

Properties：

```yaml
gateway:
  trusted-proxies:
    - 172.16.0.0/12
```

行为：

```text
remoteAddress 不属于 trusted proxy
→ 完全忽略 X-Forwarded-For
→ identity = remoteAddress

remoteAddress 属于 trusted proxy
→ 接受由该代理写入的 canonical XFF
```

Compose direct-client 测试默认不配置 trusted proxies。

* [ ] untrusted caller 伪造 XFF：不能绕过限流。

* [ ] 两 gateway 副本、同 client identity：共享配额仍成立。

* [ ] trusted proxy case：正确取得 forwarded client IP。

* [ ] 若存在 authenticated principal，为下单 route 增加 user-based second-level limiter 属于后续 enhancement，不阻塞本任务。

* [ ] 提交：

```bash
git commit -m "fix(gateway): trust forwarded IPs only from known proxies"
```

---

# Sub-plan 5 — Multi-instance operational hardening

## Task 13: A4 / D1 / D3 — Readiness、trace 与连接预算

### A4 readiness

每个业务服务：

```text
/actuator/health/readiness
```

Compose healthcheck 必须检查真正 readiness，而不是仅 JVM process alive。

多实例测试门禁继续保留：

```text
Nacos healthy instance count >= expected replica count
```

因为：

```text
service ready ≠ entire topology ready
```

### D1 tracing

当：

```text
ZIPKIN_ENDPOINT=""
```

时禁用 exporter，不创建指向空 URI 的 reporter。

有 endpoint 时才：

```text
management.tracing.export.zipkin.enabled=true
```

### D3 connection budget

先处理连接预算，不在本计划中做 PostgreSQL HA。

目标：

```text
3 replicas × 8 services
```

理论连接池 max 总量不得超过 PostgreSQL `max_connections` 的 70–75%，为 admin/migration/background 留余量。

对 order 当前 `100/instance` 先按实际负载收缩，不通过简单把 PG max_connections 拉高掩盖问题。

* [ ] 2-replica idle/burst snapshot。

* [ ] 调整各服务 pool size。

* [ ] 3-replica stack 启动。

* [ ] 记录 idle / 300VU burst `pg_stat_activity`。

* [ ] 确认 connection acquisition timeout = 0。

* [ ] 提交：

```bash
git commit -m "chore(runtime): harden multi-instance readiness and connection budgets"
```

---

## Task 14: C2 / C6 / C8 — Order aggregate concurrent mutation

这三个问题在 P0 修完后单独处理，不和前面的事务/消息改造混在同一个 PR。

### C2

对同一个 `trade`：

```text
REST cancel
vs
promotion ack
```

构造确定性 race IT。

不要简单给包含外部调用的整个 `cancelTrade()` 加 `@Retryable`。

将 mutation 拆为：

```text
load/reload aggregate
→ local state transition/CAS
→ commit
→ idempotent external side effect
```

乐观锁冲突只重试纯数据库 state-transition 部分。

### C6

`OrderTimeoutScheduler` 改成 work claiming：

```text
PENDING/UNPAID rows
→ claim batch
→ each trade exactly one worker
```

timeout cancellation 使用稳定 key：

```text
timeout-cancel:<tradeId>
```

不要用随机 UUID。

### C8

构造：

```text
confirm receipt
vs
package delivered
```

双写 race test。

接口只有在所有目标 shop orders 已经：

```text
SUCCESS
或已是合法等价终态
```

时才能返回 success。

如果部分未推进：

```text
409 / explicit partial result
```

不得静默 200。

* [ ] C2 deterministic race test。

* [ ] C6 two-scheduler claim test。

* [ ] C8 deterministic race test。

* [ ] 连续执行每个 race test ≥100 次。

* [ ] 运行全量 order E2E。

* [ ] 提交为独立 commits，不合成一个巨型 commit。

---

## Task 15: C13 / C14 / C7 — 降级为 contract / hardening

### C13

C15 修复以后：

```text
同一张券最终只会有一个 COMMITTED winner
```

已经满足 correctness。

产品选择保持异步裁决时，API 必须表达：

```text
promotionCommitStatus=PENDING
```

而不是让客户端理解成“优惠已最终确定”。

本轮不强制把 coupon reservation 前移到 quote。

### C14

quote 成功后立即将 compensation responsibility 建立起来。

失败链：

```text
quote succeeded
→ later failure
→ release quote
```

不再等待 5 分钟 expiry 才清理。

### C7

Kafka startup rebalance 属 operational behavior。

只记录：

```text
rebalance duration
consumer unavailable duration
lag recovery time
```

除非超过定义 SLA，否则不作为 release blocker。

---

# Final regression gate

所有 P0/P1 修改完成后，统一执行：

```bash
make e2e
make e2e-multi
make consistency-multi
make retry-multi
make chain-multi
make resilience-multi
make load-multi VUS_LEVELS="100 300" DURATION=30s
```

然后追加 3-replica：

```bash
make multi-up MULTI_REPLICAS=3
make e2e-multi MULTI_REPLICAS=3
make consistency-multi MULTI_REPLICAS=3
```

最终必须满足：

```text
Topology:
  8 services × N healthy replicas

Correctness:
  oversell = 0
  ID unique violation = 0
  outbox normal duplicatePublish = 0
  same-key/same-body = deterministic replay
  same-key/different-body = 409
  failed request can safely retry
  OTP cross-replica works
  OTP concurrent consume exactly once

Messaging:
  payment events consumed
  poison events bounded retry + DLT
  Kafka temporary outage recovers automatically
  no orphan inventory beyond configured recovery SLA

Availability:
  kill one replica → GET failure = 0
  no request routed to stale instance beyond defined SLA

Gateway:
  all public domain routes reachable through gateway
  untrusted XFF cannot bypass rate limit

Resource invariants:
  failed placement leaks 0 inventory
  partial multi-shop failure releases successful reservations
  unpaid expiry restores sellable quantity

Performance:
  no correctness failure at 100 / 300 VU
  record p50/p95/p99/RPS, but latency regression alone does not override correctness failures
```

只有上述 gate 全绿，才把该版本定义为：

> **multi-instance distributed correctness verified**

而不是仅仅：

> **multi-instance stack starts successfully**

---

# Recommended commit sequence

```text
1  fix(promotion): bind coupon query parameters explicitly
2  fix(order): make business IDs node-independent
3  fix(order): preserve domain conflict HTTP semantics
4  fix(order): make idempotency transactional and fingerprint-aware
5  fix(outbox): claim events atomically across replicas
6  fix(payment): enforce manual ack and bounded Kafka recovery
7  fix(order): recover critical outbox events after broker outages
8  fix(auth): bind audit IP as PostgreSQL inet
9  fix(auth): consume OTP atomically in Redis
10 fix(gateway): align public routes with service contracts
11 fix(gateway): fail over safe reads across replicas
12 fix(gateway): trust forwarded IPs only from known proxies
13 fix(order): claim timeout work across replicas
14 fix(order): resolve aggregate mutation races
15 chore(runtime): harden multi-instance readiness and connection budgets
```

每一个 commit 都必须能够单独 review，并有对应测试证据。不要做一个“fix distributed issues”的巨型提交。
