# 多实例（真分布式）测试：阻塞点清单

> 日期：2026-09-13
> 目标：让 tiny-store 跑在**每服务多副本**的拓扑上（而不是单机单实例），并逐条找出阻止它成立的问题。
> 方法：全部结论来自实机运行证据（Compose 2 副本栈 + Nacos 实例表 + 各副本自身指标 + Kafka/DB 直查 + 容器日志），不是静态阅读。

---

## 0. 结论

多实例测试**已经可以跑通**：8 个服务各 2 副本（共 16 个注册实例），网关经 Nacos `lb://` 真实分摊流量，完整 API 套件在黑盒层全绿：

```
make e2e-multi           -> Tests run: 18, Failures: 0, Errors: 0, Skipped: 1
make consistency-multi   -> Tests run:  4, Failures: 1, Errors: 0   (并发一致性：红)
```

但"能跑"和"分布式下正确/可用"是三件事。实机压测与逐组件排查共暴露 **27 个阻塞点**（其中 **1 条已修复并验证**——C15，一行参数注解），其中：

- **6 个编排/框架层**（5 个已修，A4 未修）；
- **17 个应用正确性**（16 个未修 + C15 已修；其中 14 个有量化指标或实机故障证据）；
- **3 个可观测/运维**（未修）；
- **1 个分布式韧性**（未修：副本挂掉不会转移，E1）。

其中**只在多副本下才出现**的有 4 条：跨副本 ID 碰撞（C0）、OTP 跨副本失效（C4）、
订单取消乐观锁竞态（C2）、副本故障不转移（E1）；另有 1 条多副本必然放大的 outbox 重复投递（C3，700/2655）。

把仓库原有的 k6 压测矩阵第一次指向多副本栈后，C0 的量级被钉死了：**100 并发下单失败率 5.03%，300 并发 7.49%**，
而同期两个 order 副本收到的请求数是完全均等的 7965/7965——**负载均衡没问题，失败是 ID 碰撞造成的**。

而且这些失败**没法靠重试救回来**：`make retry-multi` 证明一次失败的下单会把幂等键在网关层和订单域层
各扣一次（见 C9），同一请求重试直接被 409 拒绝。**C0 负责制造失败，C9 负责把失败变成死路**——这就是
下单链路在多副本下的完整问题。

下单链路的幂等保护整体是"两个摆设"：**C9** 的 `releaseLock` 写了从不调用，**C11** 的请求指纹存了从不比对
（同键换个请求体会返回 200 + 上一单的标识，新单没建）。两条都实测复现。

最严重的三条：

1. **跨副本 ID 碰撞（仅多副本）**：`TradeIdGenerator` 的所有副本共用 `datacenterId=1, workerId=1` 的 Snowflake，并发下单时不同副本生成相同 ID，撞唯一约束 → HTTP 500。实测 200 并发时 197/200 成功。
2. **OTP 登录跨副本失效（仅多副本）**：OTP 只存在签发它的那个 JVM 的 `ConcurrentHashMap` 里，另一个副本对同一手机号的判定与"从未发过验证码"完全一致，N 副本下约 `(N-1)/N` 概率登录失败。
3. **支付域 100% 消费不到订单事件**（`AckMode` 配置与消费者方法签名不匹配），事件重试 9 次后被静默丢弃。

另有 **outbox 重复投递**被压测量化复现：单轮 2655 条消息中 **700 条是重复发布**（同 `eventId` 发两次）。

---

## 1. 已建成的多实例测试能力

### 新增文件

| 文件 | 作用 |
|---|---|
| `docker/docker-compose-multi.yml` | 叠加编排：去掉 `container_name` 与固定端口，按副本数发布端口范围 |
| `tests/api/.../DistributedMultiInstanceIT.java` | 分布式断言：注册副本数、流量分摊、跨副本幂等 |
| `tests/performance/.../InventoryOversellBoundaryIT.java` | 改造为可跨 inventory 副本轮询打请求（`-Dinventory.replica.urls`），单实例行为不变 |
| `docker/failover-probe.py` | 副本故障探针的结果分析器（判断"是否真的故障转移"） |
| `docker/order-retry-probe.sh` | **下单链路**幂等语义探针：① 失败后同键重试能不能成功（C9）；② 同键换请求体会不会被拒绝（C11） |
| `docker/order-chain-invariants.sh` | **下单链路**资源不变量门禁：失败不泄漏库存、多店铺部分失败补偿、超时释放归还（§1 第六件事） |
| `Makefile`: `multi-up` / `multi-down` / `e2e-multi` / `consistency-multi` / `resilience-multi` / `load-multi` / `retry-multi` / `chain-multi` | 起栈 / 停栈 / 多实例 E2E / 并发一致性 + outbox 探针 / 副本故障韧性探针 / k6 压测矩阵 / 下单失败重试探针 / **下单链路资源不变量门禁** |

### 怎么跑

```bash
make e2e-multi              # 默认 2 副本 × 8 服务
make e2e-multi MULTI_REPLICAS=3   # 3 副本（需同步放宽 compose 里的端口范围宽度）
make consistency-multi      # 多实例并发一致性 + outbox 重复投递探针 + ID 碰撞探针
make resilience-multi       # 在流量中杀掉一个副本，测故障转移窗口（会返回非 0，见 E1）
make multi-up / make multi-down   # 只起/停栈
```

`e2e-multi` 会动态解析每个副本真实占用的宿主端口，再注入测试系统属性，因此宿主机端口被占用时不会假失败：

```bash
-Dgateway.base.url=http://localhost:<gw#1>
-Dgateway.replica.urls=http://localhost:<gw#1>,http://localhost:<gw#2>
-Dorder.replica.urls=http://localhost:<order#1>,http://localhost:<order#2>
-Dproduct.base.url / -Dinventory.base.url / -Dpromotion.base.url
-Dmulti.instance.mode=true -Dmulti.instance.replicas=2
```

### 已证明的三件事（`DistributedMultiInstanceIT`，3/3 通过）

1. **真的多副本**：Nacos 中 8 个服务各 2 个 `healthy && enabled` 实例。
2. **真的在分摊流量**：向网关注入 40 次请求后，按 **每个 order 副本自己的** `http_server_requests_seconds_count` 统计，两边都 > 0（实测 21/20）。单 URL 看不出的东西，这条断言能看出来。
3. **共享状态真的是共享的**：同一个 `Idempotency-Key` 先打网关副本 #1（200），再打副本 #2，返回 **409**，DB 中该 trade 仍只有 1 行。说明幂等状态在 Redis 而不是某个节点的本地缓存里。

### 已证明的第四件事（跨副本不超卖，正向结论）

`InventoryOversellBoundaryIT` 改造后可把 500 个并发预占**轮询打到两个 inventory 副本**上，仍然精确准入 `stock` 个：

```
Oversell test will spread requests over 2 inventory replica(s)
Result: PRE_DEDUCTED=20, expected (stock)=20
```

即"`inventory_reservation` 是唯一权威"这条核心承诺**在真分布式条件下成立**——这是本次多实例测试给出的最有价值的正向结论：库存内核扛得住，坏的是它周边的 ID 生成与 outbox。

### 已证明的第五件事（多实例压测：负载均衡是好的，错误率不是）

`make load-multi` 把仓库原有的 k6 压测矩阵第一次指向**多副本栈**（`VUS_LEVELS="100 300" DURATION=30s`）：

```
VUS | RPS | avg | p95 | p99 | fail%
  100      RPS=165.9 avg=596ms p95=1029ms p99=3521ms fail=5.03%
  300      RPS=354.8 avg=833ms p95=1802ms p99=2517ms fail=7.49%

=== Per-replica traffic split (order) ===
  replica 1: 7965 requests
  replica 2: 7965 requests
```

- **负载均衡是好的**：压测期间两个 order 副本收到 **7965 / 7965**，完全均等——`lb://` 在持续压力下没有偏斜；
- **错误率不是**：5.03% → 7.49%，**随并发单调上升**；而这两个数字正好被独立路径交叉验证：
  k6 按 RPS×时长×fail% 推算约 **1048** 次失败，网关/订单日志里按"不同请求"去重统计到 **1069** 次唯一约束冲突，**吻合误差 2%**。

也就是说：**多副本并没有让这套系统更快或更稳，反而在大约 100 并发下单时就开始稳定丢 5% 的请求**——
这不是压测抖动，而是 C0 的必然结果（碰撞概率随并发上升）。

### 已证明的第六件事（下单链路的资源归还**没有**泄漏，正向结论）

专门验证"下单失败/超时之后，占用的东西有没有还回去"，三个实验全部通过：

| 实验 | 做法 | 结果 |
|---|---|---|
| 失败不泄漏库存 | 独立 SKU 库存 10，制造 **6 次**"已扣减但持久化失败"的下单（每次 deduct 后又回滚） | 之后仍精确卖出剩余 **9** 件（`succeeded=9 rejected=3`），**泄漏 0** |
| 多店铺部分失败 | 一单两店铺，SHOP_B 库存 0 → 整单失败 `STOCK_LACK: SKU-bad-*` | SHOP_A 的 5 件**如数可售**（`succeeded=5 rejected=2`），已扣减的那家被正确释放 |
| 超时释放归还 | 3 件库存被 3 笔未支付订单占满（第 4 笔被拒），随后把 `expire_at` 推到过去 | 20s 内 3 条预约 → `EXPIRED`，再下单 **200 成功** |

结论：**下单链路的毛病不在"资源归还"，而在"失败的产生"（C0）与"失败之后的处理"（C9/C10）**。
这条排除把范围收窄到"一处配置 + 两处错误处理"。

这三条不变量已经固化成**可重复的门禁** `make chain-multi`（脚本 `docker/order-chain-invariants.sh`），
不再是"当时跑过一次"的一次性结论；实测输出：

```
=== Order-placement chain invariants ===
1) failed placement must not leak stock
   anchor placed (200), then 6 forced persistence failures;
   stock=10, so exactly 9 more orders should be accepted -> accepted=9 rejected=3
   PASS
2) partial multi-shop failure must release the shop that succeeded
   two-shop order (SHOP_B out of stock) -> HTTP 500;
   SHOP_A stock=5 should still be fully sellable -> accepted=5 rejected=2
   PASS
3) expired (unpaid) reservations must return the inventory
   3 orders filled stock=3 (placed=3), 4th was rejected with 500;
   after forcing expire_at into the past: EXPIRED reservations=3, new order -> 200
   PASS
VERDICT: all order-placement chain resource invariants hold.
```

判据刻意不用"看某个字段"，而是用**"还能卖出多少件"**——这是业务唯一在意的口径。

**顺便证伪的一条假设**：我原本怀疑"quote 阶段会锁券、失败后泄漏券锁"。实测不成立——
直连 promotion 的 `checkout/quote` 返回成功，但响应里 `appliedBenefits[].lockId` 是 **null**，
`user_coupon` 仍是 `UNUSED / lock_id=NULL`。券的预占发生在 **commit** 阶段（原子条件 UPDATE），
quote 阶段根本不加锁。所以"quote 泄漏券锁"不是一个真实问题（但它引出了 C13 的窗口）。

> 顺带一个会误导人的配置：测试栈里设了 `INVENTORY_RESERVATION_EXPIRY_DEFAULT_MINUTES: 1`，
> 但**它对下单链路无效**——`TradeApplicationService` 在 reserve 请求里显式传
> `expireAt = now + reservationTtlMinutes`（订单侧配置，默认走 `order.payment-timeout-seconds: 900`）。
> 实测 `created_at 08:26:45 → expire_at 08:41:42`，即**真实 TTL 是 15 分钟**。
> 任何"等 1 分钟看预约过期"的测试写法都是错的。

---

## 2. 阻塞点清单

### A. 编排层（已修）

#### A1. `container_name` 固定 → Compose 拒绝扩副本

`docker-compose-test.yml` 每个服务都写死 `container_name: tinystore-order-test` 等。Compose 对声明了固定容器名的服务禁止 `--scale`。

**修复**：叠加层用 `container_name: !reset null` 解除。

#### A2. 固定宿主端口 → 第二个副本 `all ports are allocated`

实测首次 2 副本启动直接失败：

```
Error response from daemon: failed to set up container networking:
driver failed programming external connectivity on endpoint docker-gateway-2:
all ports are allocated
```

原因两层：一是每个副本都要绑同一个 `8080`；二是**本机 8080 已被无关容器 `sub2api` 占用**，范围 `8080-8081` 里只剩 8081，副本 #1 拿走 8081，副本 #2 无端口可分。

**修复**：改用端口范围（Compose 按副本逐个分配）+ 可配置段 `MULTI_GATEWAY_PORTS`，并把全套服务迁到 `38xxx/39xxx`。这也是为什么"8080 写死"本身就是个可移植性缺陷——`Makefile`、`AbstractE2EBase` 等多处都默认 8080。

#### A3. Flyway 并发迁移

两个副本同时启动，同时对同一 schema 跑迁移：

```
order-2  | DB: schema "tinystore_order" already exists, skipping (SQL State: 42P06)
order-2  | DB: relation "idx_order_outbox_status_created" already exists, skipping (SQL State: 42P07)
payment-2| DB: schema "tinystore_payment" already exists, skipping (SQL State: 42P06)
```

本次靠迁移脚本里的 `IF NOT EXISTS` 兜住了，但 `flyway_schema_history` 的并发写没有互斥，属于"侥幸没坏"。生产多副本应使用 Flyway 的 advisory lock / 独立迁移 Job（先迁移后放副本）。

**状态**：未修（需产品决策：迁移改 Job 还是加锁）。

#### A4. 编排里只有 gateway 有健康检查，其余 7 个应用服务都没有

实测 `docker-compose-test.yml`：`postgres` / `redis` / `kafka` / `nacos` / **`gateway`** 有 `healthcheck`；
**`auth` / `account` / `inventory` / `order` / `payment` / `product` / `promotion` 一个都没有。**

后果：

- `docker compose up -d` 对这 7 个服务"进程起来就算 Started"，不做任何就绪判断；
- 对它们写 `depends_on: condition: service_healthy` 根本不可用；
- 多实例下更危险：编排/网关会在"进程在、但还没监听"的窗口里把流量导进来。

叠加实测的启动耗时（`Started ...Application in N seconds`）与方差：

| 服务 | 实测启动耗时 |
|---|---|
| gateway | 43.9s / 48.6s |
| payment | 60.9s / 61.8s |
| account | 67.4s / 69.9s |
| auth | 68.6s / 70.6s |
| product | 曾 >60s |

更何况**方差很大**：在一次 `load-multi` 运行里，auth 在 **305s 内始终没有注册成功**（门禁超时退出），
而在另一次运行里它 70s 就注册好了——同一份配置、同样的宿主机。这也是为什么本报告里的多实例
就绪门禁必须写成"按副本数轮询 Nacos"，而不能只等健康路由。

**建议**：给这 7 个服务补 `healthcheck`（`/actuator/health`）并加 `startupProbe` 语义；
`depends_on` 改成 `condition: service_healthy`。**状态**：未修。

### B. 测试框架层（已修）

#### B4. E2E 硬编码直连服务端口，多实例栈下必然失败

`MallE2EIT` 里写死了 `http://localhost:8090` / `:13000` / `:1200`。多副本栈里每个服务端口变成一段范围（副本 #1 未必是第一个端口），这些常量指向死端口，冷启动首轮直接报错：

```
MallE2EIT.strictClosure...: ResourceAccess I/O error on GET request for
  "http://localhost:8090/api/skus/SKU_A": null
```

**修复**：改为可覆盖的系统属性 `product.base.url` / `inventory.base.url` / `promotion.base.url`（默认值保持原样，单机 `make e2e` 行为不变）。

#### B5. 缺少"是不是真分布式"的断言

原套件只对着一个 URL 发请求，单进程和 16 进程跑出来的结果完全一样——即**结构上无法证伪**。新增 `DistributedMultiInstanceIT` 补齐（见 §1）。

> 踩坑记录：第一版 `e2e-multi` 只等"网关 + 各服务健康路由可用"，但健康路由**一个实例就能满足**，
> 于是 `product` 的第二个副本还在启动（约 60s）时断言就跑起来了，出现一次假失败
> （`product-service: expected 2 healthy replicas, saw 1 of 1`）。
> 已修：① 就绪门禁先等 **Nacos 拓扑完整**（每个服务 ≥ N 个 healthy 实例）再跑用例；
> ② 断言本身改成带超时的轮询。教训很通用——**"服务可用"不等于"拓扑就绪"**，
> 多实例测试的启动门禁必须按副本数而不是按服务名来判。

### C. 应用分布式正确性（未修，附实机证据）

#### C0.【最严重·仅多副本出现】跨副本 ID 碰撞：所有副本共用同一个 Snowflake 号

`tinystore-domain-order/.../domain/service/TradeIdGenerator.java` 用 Hutool Snowflake 生成
tradeId / orderId / paymentIntentId，但 datacenterId 与 workerId 直接写死默认值：

```java
private static Integer GROUP_ID  = Integer.parseInt(System.getProperty("GROUP_ID",  "1"));
private static Integer CENTER_ID = Integer.parseInt(System.getProperty("CENTER_ID", "1"));
private static Snowflake snowflake = IdUtil.getSnowflake(GROUP_ID, CENTER_ID);
```

Snowflake 的唯一性前提是**每个节点有互不相同的 (datacenterId, workerId)**。而全仓没有任何地方设置
`GROUP_ID`/`CENTER_ID`（`grep` 只匹配到无关的 Kafka `group-id`），Compose 用 `--scale` 时各副本环境变量
也完全相同。于是**每个副本都在跑同一条 ID 序列**：同一毫秒、同一 sequence，两个副本必然吐出同一个 ID。

200 并发下单的直接后果（`make consistency-multi`）：

```
[INFO] HTTP success count: 197, failure count: 3
[ERROR] InventoryNoOversellConsistencyIT...
  [All requests should succeed (stock is sufficient for 200 concurrent orders)]
  expected: 200L  but was: 197L

=== Cross-replica ID collision probe (unique-constraint violations) ===
     21 unique constraint "payment_intent_payment_id_key"
      2 unique constraint "shop_order_order_id_key"
```

两次独立运行的失败数：**8/200**、**3/200**。失败请求全部对应唯一约束冲突，无其他错误类型混入。

**压测下的量级**（`make load-multi`，k6 通过网关持续打 2 副本）：

| VUS | RPS | p95 | 下单失败率 | 日志侧去重后的唯一约束冲突 |
|---|---|---|---|---|
| 100 | 165.9 | 1029ms | **5.03%** | — |
| 300 | 354.8 | 1802ms | **7.49%** | — |
| 合计 | — | — | — | **1069** 次（`payment_intent` 697 + `shop_order` 372） |

k6 由 RPS×时长×失败率推算约 1048 次失败，与日志侧 1069 次**吻合误差 2%**——两条独立证据链互相印证。
错误率随并发上升正是 Snowflake 同号碰撞概率的特征，也说明**它不是偶发抖动，而是随流量放大的系统性缺陷**。

**为什么这是纯多实例缺陷**：单 JVM 内只有一个 `Snowflake` 实例、sequence 单调，永远不撞；一旦横向扩展，
它必然以约 `并发数 × (副本数-1) / 4096` 量级的概率返回 500。这属于"单机测试永远发现不了"的典型问题——
正是本次多实例测试存在的意义。

**修复方向**（需选型，未替用户决定）：

1. 每副本注入不同 `CENTER_ID`/`GROUP_ID`（K8s 下按 StatefulSet 序号 / Pod 序号注入；Compose `--scale` 无法按副本注入，需改用显式多服务或 entrypoint 从主机名解析）；
2. 用 Redis `INCR` 在启动时**分配并登记**唯一 workerId（系统已强依赖 Redis，最稳）；
3. 直接换成无需节点号的 ID（ULID / UUIDv7 / 数据库序列）。

无论选哪种，都应补一条"N 副本并发下单，零唯一约束冲突"的回归用例（现成的
`InventoryNoOversellConsistencyIT` 就可以充当门禁）。

#### C1.【严重】支付域消费订单事件 100% 失败，事件被静默丢弃

`PaymentApplicationService` 的消费者签名需要手动 ack：

```java
@KafkaListener(topics = "${payment.kafka.topic.order-events}", groupId = "pay-service")
public void handleOrderEvent(String message, Acknowledgment ack) { ... }
```

但 `tinystore-domain-payment/src/main/resources/application.yml` **既没配 `spring.kafka.listener.ack-mode: manual`，也没有对应的 ContainerFactory**，且设置了 `enable-auto-commit: false`。运行时每条消息都抛：

```
Caused by: java.lang.IllegalStateException: No Acknowledgment available as an argument,
  the listener container must have a MANUAL AckMode to populate the Acknowledgment.
```

容器日志中出现 **98 次**，每条记录 `Seeking to offset N` 重试 10 次后 `Backoff exhausted`，然后被 recover 掉（offset 前进，事件永久丢失）。

同一套模式在其他域都配对了，只有 payment 漏了：

| 域 | 消费者需 `Acknowledgment` | 实际 ack 模式 | 结果 |
|---|---|---|---|
| order | 是 | `application.yml: ack-mode: manual` | 正常 |
| promotion | 是 | `application.yml: ack-mode: manual` + `KafkaConsumerConfig` | 正常 |
| inventory | 是 | `KafkaConsumerConfig` 里 `setAckMode(MANUAL)` | 正常 |
| **payment** | **是** | **无** | **全量失败** |

**建议修复**（一行，`tinystore-domain-payment/src/main/resources/application.yml`）：

```yaml
spring:
  kafka:
    listener:
      ack-mode: manual
```

> 注意：修好后需回归"事件失败不 ack → 无限重试占住分区"的行为，补 DLT 或重试上限。
> 该缺陷与副本数无关（单机同样会触发），但它是多实例栈上"事件链路是否真的通"最硬的反例，因此列在这里。

#### C2.【严重】多实例下 `cancelTrade` 与促销回执消费者抢同一行 → 500

冷启动首轮，取消接口返回 **500**：

```
order-2 | Cancel trade failed: tradeId=e9ab497f-...
org.springframework.orm.ObjectOptimisticLockingFailureException:
  Batch update returned unexpected row count from update [0]; actual row count: 0; expected: 1;
  statement executed: update tinystore_order.trade set ..., promotion_commit_status=?, ..., version=?
    where id=? and version=?
  at ...TradeApplicationService$$SpringCGLIB$$0.cancelTrade(<generated>)
```

机制（代码可证）：同一个 `trade` 聚合行有**两条并发写路径**——

- 同步命令：`TradeApplicationService.cancelTrade` → `findByTradeId` → 改状态 → `save`（`@Version` 乐观锁）；
- 异步回执：`PromotionAckConsumer.processPromotionAck` → `tradeApplicationService.applyPromotionCommitResult` → 同样 `findByTradeId` → `markPromotionCommitted` → `save`。

单进程时两者共享一个持久化上下文、窗口极小；**2 副本时是两套 JVM、两个持久化上下文**，回执由"恰好拥有该分区的那个副本"消费，与处理 REST 的副本没有协调，于是读改写交错 → 后写者 `version` 失配 → 落到 HTTP 500（既没重试，也没映射成 409）。

复现特征：**冷启动首轮命中 1 次**；预热后连续 6 次 `E2ECancelReleasesInventoryIT` + 100 次手工 create→cancel 全绿。即典型"低概率、依赖时序"的竞态，测不测得到取决于 ack 延迟。

**建议修复**：对 `ObjectOptimisticLockingFailureException` 做有限重试（幂等命令天然可重试），或把"回执更新"与"用户命令"对同一聚合串行化；至少把乐观锁冲突映射为 409 而不是 500。

#### C3.【已量化复现】Outbox 发布器无分布式锁 → 事件被重复投递

`OutboxEventPublisherScheduler` 的类注释自己写明：

> **已知风险：并发多实例无锁机制** … 多实例部署时可能导致同一条 PENDING 事件被多个实例同时取到并重复发送到 Kafka。

**根因比"没写锁"更微妙**：`OutboxEventJpaRepository.findPendingEvents` 其实写了
`... WHERE status='PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED` —— 看起来是有认领的。
但 `OutboxEventService.getPendingEvents` **没有 `@Transactional`**（类上只有另外两个方法带事务），调度器也是裸调用，
于是这条语句跑在自动提交下：**`FOR UPDATE` 的行锁在语句结束的瞬间就释放了**，两个副本自然能选到同一批行。
**把锁写在事务外面 = 等于没写。** auth 域的两个 outbox 发布器是同样的模式。

第一次小批量（34 笔）没有撞上，但**提高到并发量后稳定复现**。`make consistency-multi` 自带探针，直接从 Kafka dump 全量消息按 `eventId` 去重：

```
=== Outbox duplicate-publish probe (multi-instance) ===
  tinystore.order.general:      messages=2655 uniqueEventIds=1955 duplicatePublishes=700
  tinystore.promotion.general:  messages= 391 uniqueEventIds= 391 duplicatePublishes=  0
  tinystore.inventory.general:  messages=   0 uniqueEventIds=   0 duplicatePublishes=  0
```

另一次（200 并发下单）的统计：

```
  tinystore.order.general: messages=1440 uniqueEventIds=965 duplicatePublishes=475
  eventType histogram: {INVENTORY_RESERVE_DB: 288, PROMOTION_COMMIT: 288,
                        ORDER_CREATED: 288, TRADE_CREATED: 288, PAYMENT_INTENT_CREATED: 288}
  multiplicity histogram: {2: 475, 1: 490}     # 每个重复事件恰好被发两次
```

**结论**：288 笔下单应产生 5×288=1440 条事件，实际产生了 1440 条消息但只有 965 个不同 `eventId`——
**475 个事件被两个副本各发了一次**（每个重复事件恰好 2 份，正是"两个副本同时取到同一批 PENDING 行"的特征）。

**当前没有造成数据损坏，纯属侥幸**：这批事件的消费者是支付域，而支付域的消费本来就是坏的（C1，全量丢弃）；
一旦 C1 修好、或换成没做幂等的消费者，重复事件就会变成真实的重复扣款/重复发货。

#### C4.【严重·仅多副本出现】OTP 登录在多副本下必然跨副本失效（OTP 存在内存里）

`tinystore-domain-auth` 的 OTP 仓储只有一个实现被装配成 bean，而且是**进程内存**：

```java
@Component
@Primary                                  // 无条件覆盖其它实现
public class InMemoryOtpRepositoryAdapter implements OtpRepositoryPort {
  private final Map<String, Otp> store = new ConcurrentHashMap<>();   // 每个 JVM 一份
```

另外两个实现 `RedisOtpRepositoryPort`、`JdbcOtpRepositoryPort` **都没有任何 Spring 注解**（不是 bean，
且 `RedisOtpRepositoryPort` 的方法体还是 `throw new UnsupportedOperationException()` 的占位）。也就是说
OTP 的存储介质就是"当前这个 JVM 的 HashMap"。

**实机 A/B 实验**（auth 2 副本：auth-1 → 39001，auth-2 → 39000）：
在 auth-1 上给手机号 S 发送验证码，然后用**同一个错误验证码** `000000` 分别打两个副本，
并以"从未发送过验证码"的手机号 N 作为对照：

| 探测 | 响应 |
|---|---|
| auth-1（下发副本）+ S | `验证码验证失败: otp mismatch` → **找到 OTP 了** |
| auth-1 + N（对照） | 未找到分支（被下面的 C7 错误掩盖） |
| **auth-2（另一副本）+ S** | **与"从未发送过"完全相同** → **没找到 OTP** |
| auth-2 + N（对照） | 同上 |

结论：**验证码只在签发它的那个副本里存在**。用户拿到验证码后，验证请求落到另一个副本就是"验证码不存在"，
N 个副本下约 `(N-1)/N` 概率登录失败。这是和 C0 同一类的缺陷：**每 JVM 独有、却要求全局唯一的状态**。

**修复方向**：把 `RedisOtpRepositoryPort` 真正实现并改成 `@Primary`（或 `@ConditionalOnProperty` 按环境切换），
`InMemoryOtpRepositoryAdapter` 退化成 local/dev profile 专用。

#### C5.【与副本数无关，但会把 C4 的结论掩盖掉】auth 审计表 `ip` 列类型不匹配，`/otp/send` 直接 500

实测 `POST /api/auth/otp/send` 返回 500：

```
auth-1 | System error occurred: PreparedStatementCallback; bad SQL grammar []
Caused by: org.postgresql.util.PSQLException: ERROR: column "ip" is of type inet
  but expression is of type character varying
```

来源是 `JdbcAuditLogAdapter.java:44` 用 `ps.setString(7, event.ip())` 往 `inet` 列写字符串
（`insert into auth_audit (..., ip, ...) values (?,...)`）。验证码其实已经写进内存了，但请求仍然 500；
`verify` 的"验证码不存在"分支也会在写审计时崩掉，于是把 C6 的真实原因盖成了 SQL 错误。

该缺陷与副本数无关（单机同样触发），但它是本次多实例测试里**唯一发现的一条通用功能缺陷**——
说明这条路径在单机 E2E 里从来没被覆盖过。**修复**：`ps.setObject(7, event.ip(), Types.OTHER)`（或把列改成 varchar）。

#### C6. 定时任务缺少单实例锁：逐个核对后，只有一处真的会重复做事

第一版清单把所有无锁任务一律标红，逐个读实现后发现**不能用"有没有 ShedLock"来判断**——
要看它拿数据的方式是不是有抢占/条件更新兜底：

| 任务 | 多副本下的自我保护 | 判定 |
|---|---|---|
| `InventoryReconcileJob` | Redis `SET NX PX` 单实例锁 | ✅ |
| `CouponReceiveTaskWorker` | `updateStatus(id, NEW → PROCESSING)` 条件更新即抢占，`claimed<=0` 跳过 | ✅ |
| `QuoteExpiryTask` | `updateStatus(id, QUOTED → EXPIRED)` 条件更新 | ✅（CAS 前的 `unlockByLockId` 可能被两个副本各执行一次，但解锁本身幂等） |
| `ReservationExpiryTask` / `InventoryConfirmReconciler` | 终态迁移走版本 CAS，第二个副本改到 0 行 | ✅（仅浪费扫描） |
| `PendingCommitTimeoutScheduler` | 查询后"双检查"状态，无抢占 | ⚠️ 重复尝试，靠 `@Version` 兜底 |
| **`OrderTimeoutScheduler`** | **查询后无条件取消，且每次用 `UUID.randomUUID()` 当幂等键** | ❌ 见下 |

`OrderTimeoutScheduler.closeExpiredPaymentOrders` 的问题在于：

1. `findPendingPaymentsByTimeout("UNPAID", ...)` 是普通查询，**没有抢占**，两个副本会拿到同一批 trade；
2. 它调用的 `tradeApplicationService.cancelTrade(...)` **并不受订单域幂等保护**——`IdempotencyService.tryAcquire`
   只在 `createTrade` 里用过，cancel 路径的 `idempotencyKey` 只是被拼成库存释放的子键
   （`idempotencyKey + ":inv:..."`），起不到"这次取消是否已经跑过"的作用；
3. 它给每次取消生成**新的随机幂等键**，等于主动放弃了唯一可能去重的信号。

于是两个副本会同时尝试取消同一笔超时订单，并各自调用一次 `paymentClient.closePayOrder`。
**最终没有酿成数据损坏**，靠的是 C2 里那个 `trade` 实体上的 `@Version`：后到的写会失败并回滚，
只留下一次无效事务和一条 `Failed to closeTrade expired trade` 错误日志。

**定级说明**：C6 是"浪费 + 错误日志噪音"级别，不是损坏级别；真正会损坏数据的是 C3——
outbox 发布器没有 `@Version` 这种兜底，两个副本各自把同一批事件发出去之后才置位，
于是**实测 700/2655 条被重复投递**。两者不要混为一谈。

**建议**：`OrderTimeoutScheduler` 改成条件更新抢占（或把幂等键固定成 `timeout-<tradeId>` 并让取消路径真正消费它）。

#### C7. 启动期 Kafka 同组多成员重平衡

两个副本几乎同时加入同一 consumer group，日志出现：

```
SyncGroup failed: The group began another rebalance. Need to re-join the group.
Revoke previously assigned partitions ...
```

属瞬态、可自愈，但会让启动窗口内的消费（含 outbox 事件）出现额外延迟——正是 C1/C2 这类竞态的放大器。建议加 `group.initial.rebalance.delay.ms` 或滚动启动。

#### C8.【低频·已定位到代码路径】确认收货返回 200，却可能静默漏掉子单

在 2 副本栈上反复跑 `E2EMultiShopFlowIT`（多店铺下单→支付→发货→确认收货）**26 次，失败 1 次**：

```
expected: "SUCCESS"
 but was: "PENDING_RECEIVE"        # 两个子单都停在 PENDING_RECEIVE
```

而报文里 `POST /api/order/trades/{tradeId}/confirm-receipt` 返回的是 **200**。

代码路径（`TradeApplicationService.confirmTradeReceipt`）：

```java
for (ShopOrder shopOrder : shopOrders) {
    if (OrderStatus.PENDING_RECEIVE.equals(shopOrder.getOrderStatus())) {
        shopOrder.markAsSuccess();
        ...                       // 发布 ORDER_SUCCESS
    }
    // else：什么都不做 —— 不报错、不重试、不补偿
}
```

即"**部分成功甚至完全跳过，对外一律 200**"。调用方无从知道有子单没被推进，也没有任何机制把漏掉的子单补上。

同一张 `shop_order` 行还有**第二个写者**：包裹签收路径
（`MerchantFulfillmentService` 中"所有包裹已签收则 `markAsSuccess()`"）也会把订单推到 SUCCESS。
两条写路径 + 2 副本 = 与 C2 同一类"共享行并发写"；谁的事务先提交决定乐观锁谁失败。

**定级**：低频（≈4%，1/26），但性质是"**接口报成功、状态没推进**"——用户会看到订单永远停在待收货。
它只在多副本下被观测到（单机时代该用例一直稳定）。**未取到失败瞬间的 order 日志**，
因此只定位到代码路径，没能 100% 归因到确切触发点。

**建议**：① `confirmTradeReceipt` 对无法推进的子单返回部分成功/失败而不是 200；
② 为已付订单的终态推进加重试或补偿（参考 B1 的 `InventoryConfirmReconciler` 思路）；
③ 消除同一行的双写者（与 C2 同源）。

#### C9.【严重·下单链路】下单失败后幂等键被扣住，标准重试被 409 拒绝

这条专门针对**下单链路**：C0 让 2–7% 的下单以 500 结束，而客户端对 5xx 的标准处理就是"用同一个请求重试"。
重试能不能成功，决定了这些失败是"瞬时抖动"还是"用户卡死"。

`make retry-multi`（脚本 `docker/order-retry-probe.sh`）用**确定性**手法复现（重复 tradeId 触发一次与
ID 碰撞同类的持久化 5xx）：

```
=== Order-placement retry probe ===
  1) first placement                        : 200
  2) forced failure (same tradeId, new key) : 500
  3) retry with the SAME key, same body     : 409

  Redis state left behind by the failed attempt:
    idempotent:order-service:k-fail-...          = COMPLETED            ttl≈299s
    idempotency:trade:create:k-fail-...          = <tradeId>:<buyerId>  ttl≈598s
    idempotency:response:trade:create:k-fail-... = (不存在)

  VERDICT: BLOCKED RETRY
```

**两层各自独立地把键扣住了**，所以修一层不够：

1. **网关层**：`IdempotencyFilter` 的写法是
   `chain.filter(exchange).then(markSuccess(key)).onErrorResume(ex -> release(key)...)`。
   下游返回 5xx 时，网关拿到的是一个**正常的响应**（不是抛出的异常），于是 `markSuccess` 照常执行 →
   键被标成 `COMPLETED` → 后续同样的 key 在网关就被 409 短路，而且**没有任何缓存响应体**可以返回给客户端。
2. **订单域层**：`IdempotencyService.releaseLock(scope, key)` **方法存在但全仓零调用**；
   `createTrade` 的 catch 只做库存/优惠补偿。于是 `trade:create:<key>` 的锁要占满整个 TTL（默认 **600s**），
   而 `idempotency:response:<key>` 从来没写过 → 就算绕过网关，重试也会撞 `IDEMPOTENT_CONFLICT`。

**另外一处补偿盲区**：`compensationRequired = true` 是在**库存扣减之前**才置位的
（`TradeApplicationService` 第 245 行）。也就是说在它之前失败的路径（例如 promotion quote 阶段）
连库存/优惠补偿都不会触发。

**影响**：一次失败的下单请求 = 那个幂等键在 5–10 分钟内报废。用户重试同一请求永远被拒，
只有换一把新键才能下单——而新键意味着"这是一次新请求"，与"重试"的语义正好相反。
结合 C0 的 5–7% 失败率，**每一次 ID 碰撞都会变成一次用户侧的重试死路**。

**建议**：① 网关只在 `2xx` 时 `markSuccess`，4xx/5xx 一律 `release`（或按状态码区分：5xx 释放、4xx 保留）；
② 订单域在失败路径调用 `releaseLock`，或显式写入"失败"缓存让重试能拿到明确结果；
③ 把 `compensationRequired` 提前到"任何副作用发生之前"就置位。

#### C10.【下单链路】业务性拒绝一律返回 500：客户端分不清"别重试"和"请重试"

`TradeController.createTrade` 的 catch 只放行了 `IdempotencyServiceUnavailableException`（503），
**其余所有异常一律压成 HTTP 500**：

```java
} catch (IdempotencyServiceUnavailableException e) {
    throw e;                       // 唯一能走到 GlobalExceptionHandler 的分支（503）
} catch (Exception e) {
    log.error("Create trade failed", e);
    return ResponseEntity.status(500).body(
        OrderHttpResponse.fail(500, "Create trade failed: " + e.getMessage()));
}
```

而 `GlobalExceptionHandler` 里明明有 `DomainConflictException -> 409 CONFLICT` 的映射——对这个接口等于**死代码**。
实测三种本该是 4xx 的业务结果全部返回 500：

| 场景 | 实测响应 |
|---|---|
| 库存不足 | `500 {"code":500,"msg":"Create trade failed: Inventory deduct failed for shop: SHOP_A, msg: STOCK_LACK: ..."}` |
| 重复 tradeId（换新键提交同一单） | `500 ... duplicate key value violates unique constraint "trade_trade_id_key"` |
| 幂等冲突（同键重试，直连 order 服务） | `500 ...`（本应是 409；这也解释了上一轮"直连返回 500"的疑团） |

**为什么这条在下单链路上很要命**：它和 C9 是乘法关系——

1. 库存不足是**正常业务结果**，客户端和监控都把它当成服务端故障（计入 5xx 错误率）；
2. 规范客户端对 5xx 的处理就是**重试**；
3. 一重试就撞上 C9——幂等键已被上一次失败扣住，直接 409 死路。

于是"库存不足"这种最普通的场景，会演进成"用户重试被拒、以为系统坏了"。
**建议**：把 `DomainConflictException`（以及库存不足等业务异常）放行给 `GlobalExceptionHandler`，
只对真正的未知异常兜底 500。

#### C11.【下单链路】幂等键与请求体的绑定"只写不校验"：同键换个 body 会静默返回**上一单**

`TradeApplicationService:123` 老老实实算了请求指纹并传给幂等服务：

```java
String fingerprint = tradeId + ":" + command.getBuyerId();
if (!idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint)) { ... }
```

但 `tryAcquire` 只是把它当作锁的**值**写进去（`setIfAbsent(redisKey, fingerprint, ttl)`），**全仓没有任何地方读回来比较**；
拿到 `false` 之后调用方直接返回上一次缓存的结果。也就是说"请求指纹"这道护栏和 C9 里的 `releaseLock` 一样——
**写在那里，但从来没生效**。

实测（直连 order 服务，绕开网关，排除网关层的干扰）：

```
key=fpkey-…   bodyA -> tradeId=fpA   bodyB -> tradeId=fpB
  1) key K + body A             -> 200 {"tradeId":"fpA","paymentIntentId":"…520"}
  2) SAME key K + body B        -> 200 {"tradeId":"fpA","paymentIntentId":"…520"}   ← 返回的是 A 单
  3) DB 里查：只有 fpA 存在，fpB 从未被创建
  4) 对照：换新键 + body B      -> 200 {"tradeId":"fpB", …}                          ← 正常
  5) 走网关：同键 + 不同 body    -> 409 idempotent_conflict                          ← 网关挡住了
```

**危险点在于"假成功"**：客户端复用同一把幂等键、但请求体变了（改了购物车、改了地址），
接口返回 `200` 并给它**上一单的 tradeId / paymentIntentId**，客户端以为新单下成了——而新单根本不存在。
比报错更糟：没有任何信号提示它出了问题。

**为什么会走到这一步（时间窗口）**：网关侧 TTL 是 5 分钟（`IDEMPOTENCY_TTL: PT5M`），订单域是 10 分钟
（`order.idempotency.ttl-seconds: 600`）。所以**网关那把保护伞比 bug 先失效**——键过期后的那 5 分钟里，
请求可以穿过网关直达订单域，踩中上面第 2 步。**这不是推断，是实测**：

```
key=ttlkey-…   bodyA -> ttlA   bodyB -> ttlB          （走网关 :38080，不是直连）
  T+0s     key K + body A   -> 200 {"tradeId":"ttlA", …}
  T+340s   SAME key + body B -> 200 {"tradeId":"ttlA", …}    ← 仍然是 A 单
  DB 里只有 ttlA，ttlB 从未创建
```

即在**主下单接口**上，客户端换了个请求体、复用同一把键，会拿到 200 和上一单的标识，
新单却不存在——而且没有任何错误信号。

**建议**：在重复请求路径上把存下来的 fingerprint 读回来比对，不一致就返回 409（代码本来就是按这个设计的）；
或者干脆把请求体哈希和响应一起缓存，重放时先比对。

#### C12.【下单链路·严重】Kafka 停几分钟 → 下单照样 200，订单随后被自动取消，1 件库存永久丢失

这条是"链路的异步半边"：下单成功后，库存的 **DB 预约**是由 `INVENTORY_RESERVE_DB` 这个 outbox 事件异步创建的
（Redis 预扣是同步的）。实测把 Kafka 停掉再下一个单，链路会这样崩：

| 时刻 | 观测 |
|---|---|
| Kafka 停掉 | 下单仍然 **HTTP 200**（Redis 预扣成功、trade 落库） |
| ~2–3 分钟后 | `INVENTORY_RESERVE_DB` 达到 `retry_count=4`、状态 **`FAILED`**（终态） |
| 期间 | `PROMOTION_COMMIT` 也发不出去 → `promotion_commit_status` 停在 `PENDING` → `PendingCommitTimeoutScheduler` 判定超时 → **自动取消该订单**（`closed_at=08:48:49`，子单 `CLOSED` / `RELEASED`） |
| Kafka 恢复 +120s | 其余 7 条事件全部补发成功（`PUBLISHED`），**只有那条 `INVENTORY_RESERVE_DB` 永远停在 `FAILED`** |

事后状态（同一个 trade）：

```
trade:  pay_status=UNPAID  promotion_commit_status=PENDING  closed_at=2026-09-13 08:48:49
子单:   order_status=CLOSED   inventory_status=RELEASED      ← 投影以为库存已释放
Redis:  inventory:total:SHOP_A:<sku>=5
        inventory:deducted:SHOP_A:<sku>=1                    ← 预扣仍然被占着
DB:     inventory_reservation 里该 trade 的行数 = 0           ← 没有行可供过期任务释放
```

**根因三连**：

1. **outbox 终态不可逆**：`markAsFailed` 在 `retry_count>=3` 时置 `FAILED`，而轮询只查 `PENDING`；
   全仓**没有任何重投机制**（`findFailedEvents` 只被 `OutboxAdminController` 用来"看"）。
2. **过期任务修不了**：本应由 `ReservationExpiryTask` 兜底的预扣，靠的是 DB 里的 `inventory_reservation` 行——
   而这行正是那个丢失的事件要创建的。**没有行，就没有任何东西会去释放 Redis 的占用**。
3. **顺序反了**：Redis 预扣（同步）先于 DB 预约（异步）生效，中间这段窗口没有补偿。

**实测影响**：该 SKU 库存 5 件，此后只能卖出 **4 件**，第 5 件起永久 `STOCK_LACK`：

```
SKU SKU-kafka-…: redis total=5 deducted=1, DB total=5, 无预约行
succeeded=4 rejected=3        ← 1 件库存永久丢失
```

**顺带一个放大器**：broker 挂掉时 `KafkaProducer.send()` 会**阻塞**（默认 `max.block.ms=60s`），
而 outbox 轮询是单线程 fixed-delay——所以不只是那条事件失败，**整个 outbox 会一起卡住**，
"3 次重试"实际耗时以分钟计。

**建议**：① 失败事件可重投（管理接口 + 定时复活，或直接落到 Kafka 的出站队列由外部兜底）；
② 给"已预扣但无 DB 预约"加一条对账（`InventoryReconcileJob` 现在只扫 DB 表）；
③ 把 `INVENTORY_RESERVE_DB` 的确认纳入下单同步路径，或让 Redis 预扣带上 TTL/来源标识以便自愈。

#### C13.【下单链路·已验证】券在 commit 阶段才预占 → 并发用同一张券会"先全部下单成功"，最终只胜出一单

先给结论：**促销 quote 阶段不加锁**（这条假设被证伪，见 §1 第六件事后面的说明），
券的预占发生在 **commit** 阶段，而且是原子的（`findFirstByUserIdAndCouponIdAndUseStatusForUpdate` 行锁 +
`lockUnused` 条件 UPDATE，只有 `UNUSED` 能被改成 `LOCKED`）。所以"券被锁两次"不会发生。

但 commit 是**异步**的（order 写 `PROMOTION_COMMIT` outbox → promotion 消费 → 预占 → 回 ack），
于是"下单"和"占券"之间有一个**几秒级的窗口**。实测：同一个用户、同一张**一次性**券，**5 个并发下单**：

```
5 个请求全部 HTTP 200，5 张 trade 全部带上抵扣：
  trade_id        discount  payable  promotion_commit_status
  dbl-…-1           1000       0      PENDING
  dbl-…-2           1000       0      PENDING
  dbl-…-3           1000       0      PENDING
  dbl-…-4           1000       0      PENDING
  dbl-…-5           1000       0      PENDING
user_coupon:      UNUSED  lock=NULL
coupon used_stock: 0
```

即"券还没被占，但已经有 5 张单按券价下单成功了"。设计上由异步 commit 裁决谁活下来，输家会被
`PendingCommitTimeoutScheduler` 自动取消——本次实测这 5 单确实在约 40s 后**全被取消**
（`closed_at` 全部非空、5 条 `checkout_quote` 全部 `RELEASED`）。

**后续已查清并验证**：这些券单的 commit 之所以连 ack 都不产生，是因为 commit 路径里的
`findFirstByUserIdAndCouponIdAndUseStatusForUpdate` **少了一个 `@Param("couponId")`**（C15），
一进去就抛异常、重试耗尽后进 DLT。**C15 修好之后重跑这个实验，裁决者的行为是对的**：

```
5 个并发券单全部 HTTP 200（窗口期确实存在）
45s 后：
  trade_id    promotion_commit_status  closed  discount
  arb-…-1     PENDING                  t       100
  arb-…-2     PENDING                  t       100
  arb-…-3     PENDING                  t       100
  arb-…-4     COMMITTED                f       100     ← 唯一胜出
  arb-…-5     PENDING                  t       100
  user_coupon:  LOCKED（只有一个 lock_id）
  checkout_quote: COMMITTED ×1 + RELEASED ×4
```

即 **"同一张一次性券最终只会被一单占用"这个不变量成立**（原子条件 UPDATE 起了作用），
输家会被自动取消、报价被释放，券没有被重复抵扣。

**所以这条的定性是**：设计上的最终一致性是对的，**唯一的代价是那个几秒窗口里"下单成功"是假的**——
用户会看到 5 次下单成功、几十秒后 4 次消失。对电商来说这是可接受的（类似秒杀），
但如果要消除，就得把券的预占提前到下单阶段（`lockId`/`LOCKED` 机制本来就是现成的）。

**建议**：把券的预占提前到 quote/下单阶段（本来就有 `lockId` 字段和 `LOCKED` 状态，机制是现成的），
或者在下单响应里明确告知"优惠待到账确认"。

#### C14.【下单链路·小】补偿标志置位之前的失败会留下一张 `QUOTED` 报价单

`compensationRequired = true` 在**库存扣减之前**才置位，所以更早的失败不会走补偿。
实测触发了一次更早的失败（promotion quote 返回 `requires re-quote`）：

```
POST /api/order/trades  -> 500 {"msg":"Create trade failed: Promotion quote requires re-quote due to changes: …"}
之后 promotion.checkout_quote 里该用户留下 1 行 status = QUOTED（不是 RELEASED）
```

这张报价单只能等 `QuoteExpiryTask`（`fixedDelay PT5M`）兜底，也就是最多残留 5 分钟。
因为本次没有券被锁住，影响限于"多留一行报价 + 5 分钟后才清理"；但机制上说明
**"下单失败"并不保证"报价被释放"**。**建议**：把 `compensationRequired` 提前到 quote 成功之后立刻置位。

#### C15.【下单链路·严重·一行 bug·✅已修复并验证】用券的下单必然失败：promotion commit 查询缺 `@Param` → 事件进 DLT → 订单被自动取消

这是 C13 那个"券单全被取消"的真正原因，不是数据问题，是**参数绑定写错**：

```java
// JpaUserCouponRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT uc FROM UserCouponEntity uc WHERE uc.userId = :userId AND uc.couponId = :couponId AND uc.useStatus = :unused")
Optional<UserCouponEntity> findFirstByUserIdAndCouponIdAndUseStatusForUpdate(String userId, UUID id, String unused);
//                                                                                    ↑ 参数名是 id，且没有 @Param("couponId")
```

查询里引用 `:couponId`，方法参数却叫 `id`、也没有 `@Param` 注解。Spring Data 按参数名绑定 → 运行时直接抛
**`No argument for named parameter ':couponId'`**。

而这个方法正是**券预占**（commit 路径）要调用的那一个，于是整条链路这样断掉：

| 观测 | 证据 |
|---|---|
| 用券下单 | `POST /api/order/trades` → **200**，`payableAmountCents=900`（¥1 券已抵扣） |
| promotion 处理 commit | 重试 10 次后投进 **DLT**；DLT 头里写着异常：`kafka_dlt-exception-message: … No argument for named parameter ':couponId'`，消息体正是该单的 `PROMOTION_COMMIT`（`tradeId=cpn3-…`） |
| 订单侧 | 永远收不到 ack → `promotion_commit_status` 停在 `PENDING` → `PendingCommitTimeoutScheduler` **自动取消订单** |
| 券 | 始终 `UNUSED`、`used_stock=0`（因为预占那步就抛了） |
| 对照（不带券的下单） | **5 秒内 `COMMITTED`**，一切正常 |

**结论：任何使用优惠券的下单，都会在"下单成功"之后被系统自己取消**，券也永远用不掉。
现有 E2E 套件之所以没发现——那些用例**从不传券**（`platformCouponCodes` / `shopCouponCodesByShop` 一直为空）。

> 单测为什么也没拦住：`OrderEventConsumer` 那层单测**把 repository 打了 mock**
> （日志里能直接看到 `Order event processed and acknowledged`），所以"查询参数绑不上"这种事
> 只有真的连库才会炸。**要发现这类问题，必须有一层真实链路的测试**——这正是本次多实例测试的价值所在。

**修复（已实施）**：

```java
Optional<UserCouponEntity> findFirstByUserIdAndCouponIdAndUseStatusForUpdate(
    @Param("userId") String userId,
    @Param("couponId") UUID couponId,
    @Param("unused") String unused);
```

> 这是本次多实例测试过程中**唯一改动的一处生产代码**：一个参数注解。改动只会把"调用必抛异常"
> 变成"正常工作"（该方法在修复前 100% 失败），因此不存在行为回退面。其余 25 条阻塞点只记录、未修改。

**修复后实测**（重建 promotion 镜像 + 重跑同一实验）：

```
用券下单 -> HTTP 200, payable=900
  T+5s :  COMMITTED  closed=false  payable=900      ← 修好前这里是永远 PENDING
  T+10s:  COMMITTED  closed=false
  T+15s:  COMMITTED  closed=false
  T+20s:  COMMITTED  closed=false
user_coupon: LOCKED lock=plk:8765c635e158ffb13d9f400f5c1a   ← 修好前是 UNUSED/NULL
```

券单能正常 commit、券被正常预占，C13 的裁决实验也随之跑通（见 C13）。

> 同类隐患：同一个接口里 `markAsUsed(UUID id, String unused, String used, String tradeId, LocalDateTime now, LocalDateTime now1)`
> 也没有 `@Param`，只是**参数名恰好和查询里的命名一致**才没炸——属于"靠巧合工作"的写法，值得一起规范化。

**这一类的系统排查**：写了个小解析器，把 order / promotion / inventory / payment / product / account / auth
七个模块里**所有**带命名参数的 `@Query` 方法都拉出来比对"查询里的 `:name`" vs "签名里真实可用的参数名"。
结果：**全仓只有这一处不匹配**（其余方法要么有 `@Param`，要么裸参数名恰好一致）。
也就是说这个 bug 是孤例，不用怀疑还有一串同类地雷；但它偏偏落在"用券下单"这条唯一的路径上。

#### C16.【下单链路入口】网关 IP 限流可被 `X-Forwarded-For` 绕过（身份取自客户端可控头）

下单链路的第一道闸是网关的 IP 限流。它的身份来源是：

```java
private Optional<String> resolveIdentity(ServerWebExchange exchange) {
    String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
        return Optional.of(forwarded.split(",")[0].trim());   // 没有任何可信代理校验
    }
    ...
}
```

而 `identity` 是限流 token bucket 的 key 的一部分（`routeId + identity`），**所以身份由客户端自己说了算**。
把测试栈一直关着的限流打开（新增 `MULTI_GATEWAY_RATE_LIMIT_ENABLED=true`）后实测：

| 实验 | 请求 | 结果 |
|---|---|---|
| A 同一身份（不带 XFF） | 200 并发 | 174×404 + **26×429**（限流生效） |
| B 每次轮换 `X-Forwarded-For` | 200 并发 | **200×404，0×429 —— 完全绕过** |
| C 同身份、均分到**两个网关副本** | 200 并发 | 170×404 + **30×429** |

**两个结论**：

- ✅ **多实例正结论**：token bucket 在 Redis 里，两个副本共享同一配额（A 的 26 ≈ C 的 30，量级一致），
  不存在"副本数把配额放大 N 倍"的问题；
- ❌ **可绕过**：客户端只要每次换一个 `X-Forwarded-For` 就永远进不了限流；反过来还能**固定成受害者的 IP**
  把对方配额打满（定向 DoS）。因为配额是集群共享的，这个绕过影响的是**整个集群**的限流。

**为什么一直没被发现**：测试栈默认 `GATEWAY_RATE_LIMIT_ENABLED=false`，这段代码从来没在运行态跑过。
**建议**：只信任来自已知反向代理的 XFF（或在边缘重写该头），客户端直连场景用 remote address。

#### C17. 附：针对"每 JVM 独有、却要求全局唯一"这类状态做了一轮系统排查

C0 与 C4 是同一类缺陷（单个 JVM 的状态被当成了全局状态）。为避免只抓到这两个，把全仓所有
"每个进程各自持有"的状态逐个核对了一遍：

| 组件 | 存储介质 | 多副本是否安全 |
|---|---|---|
| 网关请求幂等 `gateway/idempotency/IdempotencyService` | Redis，失败才降级本地 Caffeine | ✅（已用跨网关 409 实测） |
| 网关 IP 限流 `RedisRateLimiterService` | Redis Lua 令牌桶，key 由 `routeId+identity(IP)` 组成 → 多副本共享同一配额（**已实测**：同身份单网关 26 拒 vs 双网关 30 拒）；仅在 Redis 不可用时降级本地 | ✅ 配额共享；❌ 但 `identity` 取自客户端可控的 XFF（C16） |
| 促销幂等 `DbIdempotencyStorage` | **DB**（`@Primary`）；`InMemoryIdempotencyStorage` 只是非主 bean | ✅ |
| 促销券锁定 `lockId/lockExpireTime` | DB 列 + 过期清理任务 | ✅（清理任务本身见 C6） |
| 库存预约/扣减 | DB 表 + Redis 前置闸门，`inventory_reservation` 为权威 | ✅（已用跨副本不超卖实测） |
| 库存对账 `InventoryReconcileJob` | Redis `SET NX PX` 单实例锁 | ✅ |
| 订单 outbox 发布 | DB 表，但**无认领无锁** | ❌ C3（已量化 700/2655 重复） |
| 订单 ID 生成 `TradeIdGenerator` | **JVM 内 Snowflake，节点号恒为 1/1** | ❌ C0（已量化：8/200、3/200 撞唯一约束） |
| 认证 OTP `InMemoryOtpRepositoryAdapter` | **JVM 内 ConcurrentHashMap（`@Primary`）** | ❌ C4（已用 A/B 实测跨副本不可见） |
| 认证 OTP 请求锁 `RedissonLockAndRateLimitAdapter` | Redis | ✅ |
| 认证令牌吊销 outbox | DB 表，发布器同样无锁 | ❌ 同 C3 模式（未单独复现） |
| 网关令牌吊销校验 `TokenVersionFilter` | **每次请求读 Redis** `tinystore:security:credential-version:<userId>`，不缓存判定结果 | ✅ |
| 网关 JWKS `JwkCacheService` | 每副本内存缓存公钥，按 `JWKS_CACHE_TTL` 过期重取 | ✅（只读、会收敛；代价是密钥轮换传播有 TTL 窗口，非正确性缺陷） |

结论：**共 3 处真正的"假全局状态"**（订单 ID、OTP、outbox/auth 发布器无锁），其余该走共享存储的都走对了。

> 备注：网关限流这一行是**代码级结论**——测试栈用 `GATEWAY_RATE_LIMIT_ENABLED=false` 关掉了限流，
> 所以它没有参与实机验证。要把它也变成实测结论，需在多实例栈打开限流后用两个网关副本打同一 IP 的配额，
> 观察总配额是否仍是配置值（而不是 N 倍）。

### D. 可观测 / 运维（未修）

#### D1. 追踪导出配置为空时直接丢 span

多副本下每个实例都在刷：

```
AsyncReporter.java:284 [WARN] Dropped 3 spans due to IllegalArgumentException(URI with undefined scheme)
```

`management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:}` 为空时，Brave 仍然建立了 `AsyncReporter`，于是每批 span 都因 URI 非法被丢弃。多副本排查跨服务问题恰恰最依赖 trace。**修复**：endpoint 为空时完全禁用 zipkin reporter，或给一个可用的默认值。

#### D2. 网关路由与服务实际路径不一致，导致"统一入口"不成立

这是 E2E 只能直连端口（B4）的根因。实测：

```
# 1) /api/skus/**  —— 网关没有这条路由
GET  http://<gw>/api/skus/SKU_A        -> 404（网关自身返回，无路由）
GET  http://localhost:38090/api/skus/SKU_A -> 200（直连正常）

# 2) /api/inventory/** —— 有路由但前缀被多剥了一层
POST http://<gw>/api/inventory/reservations/reserve -> 404
     {"status":404,"error":"Not Found","path":"/inventory/reservations/reserve"}
     而服务端 controller 是 @RequestMapping("/api/inventory/reservations")

# 3) /api/products/** —— 带齐 header 时也不通
GET  http://<gw>/api/products/prod-1  (X-Shop-Id: SHOP_A) -> 500 INTERNAL_ERROR
GET  http://localhost:38090/api/products/prod-1 (同 header) -> 200
```

`order-service` 之所以工作，是因为它的 controller 恰好是 `/order/trades`（与 `StripPrefix=1` 后的路径一致）；`account` 的 `StripPrefix=1` 对应 controller 也一致。也就是说**网关路由的 strip 策略在不同域之间不一致**（auth 用 `StripPrefix=0`，order/account 用 1 且控制器不带 `/api`，inventory 用 1 但控制器带 `/api`）。

**影响**：任何"经统一入口压测/验证"的方案都跑不通；多实例下更不能用直连端口（那样只打到某一个副本）。

#### D3. 有状态层仍是单点，且连接数按副本数线性放大

`postgres / redis / kafka / nacos` 仍是单实例，8 个域共用一个 PG（每域一个 schema）与单 Redis。多副本后连接池按副本翻倍：

```
postgres max_connections = 500
order   Hikari max = 100 × 2 = 200
inventory Hikari max =  25 × 2 =  50
promotion Hikari max =  25 × 2 =  50
其余 5 个服务 ~10 × 2 = 100
合计 ≈ 400（+ 管理连接），已贴近 500
```

**影响**：副本数再往上加会先撞连接数上限，而不是撞 CPU。这本身也是"分布式测试的真实边界"之一：共享存储层是当前规模上限。

**实测补充**（`pg_stat_activity`，2 副本）：

| 场景 | 实际连接数 / max_connections |
|---|---|
| 空闲 | **143 / 500**（≈29%） |
| 120 并发下单突发 | **274 / 500**（≈55%） |

按同一负载线性外推到 3 副本约 410，若并发再上一个档次就会顶到 500；而按"每个池都打满"的上限算是
`100×3 + 25×3 + 25×3 + 10×3×5 = 600 > 500`——**所以副本数的硬上限不是 CPU，而是 PG 的 `max_connections`**。

---

### E. 分布式韧性（未修）

#### E1.【严重】副本故障不会转移：约一半请求失败，且持续 20s 以上

多副本的价值有一半在于"挂一个不影响服务"。用 `make resilience-multi` 在流量中杀掉一个 order 副本，
网关侧观测结果：

```
=== Replica-failure probe result ===
  probes=49  ok=46  failed=3  (6.1% failure)
  kill happened at T+10.0s
  first failure T+9.9s  last failure T+30.6s  -> failures persisted 20.6s after the kill
  failure codes: ['000', '500']
  slowest failed request: 20010ms (successful median: 217ms)
  VERDICT: NO FAILOVER -- requests routed to the dead replica fail
           instead of being retried on a healthy one.
```

- **丢失的请求全部落在"恰好轮询到那个死副本"的那一半**（另一副本此时完全健康）；
- 成功请求中位数 **217ms**，失败请求要么 **~3.3s 后拿到 500**，要么**直接挂住到客户端超时**（实测 20s 未返回）；
- 失败不是瞬间的：**最后一笔失败发生在副本死亡后 20.6s**，更早一次实验是 10.4s 的失败窗口。

网关侧日志证实是连不上死实例，而不是业务错误：

```
gateway-2 | CompositeLog.java:102 [ERROR] [zb...] 500 Server Error for HTTP GET "/api/order/trades/q-2"
gateway-2 |   at io.netty.channel.unix.Errors.newConnectException0(Errors.java:158)
gateway-2 | ServiceInfoHolder.java:225 [INFO] removed ips(1) ... 172.29.0.19   # Nacos 07:21:45.266 已移除
gateway-2 | CompositeLog.java:102 [ERROR] ... 500 Server Error ... q-2          # 07:21:48.413 仍在打
```

**根因（两条叠加）**：

1. **没有任何跨实例重试**——全仓搜不到 `spring.cloud.loadbalancer.retry`，Feign 也没配 `retryer`
   （Spring Cloud OpenFeign 默认 `Retryer.NEVER_RETRY`）。选中谁就是谁，连不上就直接失败。
2. **负载均衡实例列表有本地缓存**——`spring.cloud.loadbalancer.cache.ttl` 未配置，用默认 **35s**。
   Nacos 客户端已经把死实例从列表里删掉了（日志 07:21:45），但网关 LB 的缓存仍把那个 IP 当可用实例返回，
   于是移除之后还继续往上打（07:21:48）。

**为什么这条重要**：这不是"极端场景"。K8s 滚动发布、节点驱逐、OOM 重启都会产生同样的窗口，
表现为**发布期间约 1/N 的请求 500/超时，持续几十秒**。单机栈永远测不出来，因为它只有一个实例，
挂掉就是全挂，反而"不会部分失败"。

**建议**：① 配 `spring.cloud.loadbalancer.retry`（只对幂等方法开 `retry-on-all-operations`，或限定
`retry-on-status-codes`）并显式设 `cache.ttl`；② Feign 侧配 `Retryer` + 连接/读取超时；
③ 更彻底的是在网关引入 Resilience4j 断路器 + 重试，并给实例加健康探测剔除（outlier ejection）。

## 3. 复现与验证命令

```bash
# 起 2 副本栈
make multi-up

# 探活与副本注册数
curl -s "http://localhost:8849/nacos/v1/ns/instance/list?serviceName=order-service&healthyOnly=false" | jq '.hosts | length'

# 全量 E2E + 分布式断言
make e2e-multi

# 只看分布式断言
cd tests/api && ../../mvnw verify -Pit -DskipITs=false -Dit.test=DistributedMultiInstanceIT \
  -Dmulti.instance.mode=true -Dnacos.base.url=http://localhost:8849 ...

# 多实例并发一致性 + 两个探针（seed 由目标自动应用）
make consistency-multi

# 副本故障韧性探针（流量中杀掉一个副本，测故障转移窗口）
make resilience-multi

# 多实例 k6 压测矩阵 + 每副本流量分配 + ID 碰撞探针
make load-multi                 # 默认 VUS_LEVELS="100 300" DURATION=30s，可用环境变量覆盖

# 下单链路：一次下单失败后，同一把幂等键还能不能重试（确定性，不依赖随机碰撞）
make retry-multi

# 下单链路资源不变量门禁（当前全绿：失败不泄漏库存 / 多店铺补偿 / 超时归还）
make chain-multi

# 单副本对照（默认行为不变）
make e2e
```

`consistency-multi` 的输出里有三块可直接引用的证据：

1. `Tests run: 4, Failures: ?` —— 并发一致性套件的通过情况；
2. `=== Outbox duplicate-publish probe ===` —— 每个 topic 的 `messages` / `uniqueEventIds` / `duplicatePublishes`；
3. `=== Cross-replica ID collision probe ===` —— 按唯一约束名聚合的跨副本 ID 碰撞次数。

`resilience-multi` 的输出是一份"故障转移判决"：探测总数、失败比例、失败相对击杀时刻的时间窗、
失败状态码分布、失败请求耗时 vs 成功请求中位数，以及 `VERDICT`。
只要还有请求落在死副本上就返回非 0（当前为 2），可以当门禁用。

`load-multi` 输出三段：k6 每档 VUS 的 `RPS / avg / p95 / p99 / fail%`（交叉阈值会置 `THRESHOLD-CROSSED`
并让目标以非 0 退出）、order 两个副本各自收到的请求数（判断负载是否偏斜）、
以及按"不同请求"去重后的跨副本 ID 碰撞次数（与 k6 的失败数互为交叉验证）。

`retry-multi` 输出两份判决：

- **Check 1（C9）**：首次下单状态、强制失败状态、同键重试状态，以及失败后 Redis 里两层幂等键的残留
  （网关 `COMPLETED` / 订单域锁 / 是否有缓存响应）；
- **Check 2（C11）**：同键 + 不同请求体的响应状态、实际返回的 tradeId、以及该 tradeId 是否真的落库。

任一缺陷复现就返回非 0（当前为 2）。实测输出：

```
=== Check 1: retry after a failed placement (C9) ===
  1) first placement                     : 200
  2) forced failure (same tradeId, new key): 500
  3) retry with the SAME key, same body  : 409
  Redis left behind: gateway=COMPLETED | order-lock=held | response=ABSENT
  --> BLOCKED

=== Check 2: is the key bound to the request body? (C11) ===
  2) SAME key + body B       : 200 (served tradeId=fpA-…)
  3) trades actually created with tradeId=fpB-…: 0
  --> FALSE SUCCESS

VERDICT: defects reproduced (retry_blocked=1, fingerprint_blocked=1).
```

---

## 4. 建议处理顺序

| 优先级 | 项 | 理由 | 成本 |
|---|---|---|---|
| P0 | **C0 跨副本 Snowflake 号碰撞** | **多副本并发下单直接 500，单机永远测不出** | 中（需选型 + 注入 workerId） |
| P0 | **C4 OTP 存内存，跨副本登录失效** | **N 副本下约 (N-1)/N 概率登录失败** | 中（把 Redis 实现补完并改 `@Primary`） |
| P0 | C1 支付 `ack-mode: manual` | 事件 100% 丢失，静默失败 | 一行 |
| ~~P0~~ | ~~C15 用券下单必然被取消（缺 `@Param`）~~ | **✅ 已修复并验证**（补 `@Param("couponId")`，券单已能正常 commit） | 已完成 |
| P0 | D2 网关路由路径对齐 | 统一入口不成立，是 E2E 直连端口的根因 | 中（需定 strip 规范 + 回归） |
| P0 | **C9 下单失败后幂等键被扣住** | **把 C0 的每次失败放大成用户重试死路；改动小** | 小～中（网关按状态码释放 + 订单域 releaseLock） |
| P0 | **C10 业务拒绝一律 500** | 库存不足被当服务故障、诱导客户端重试→撞 C9 | 小（放行业务异常给全局处理器） |
| P0 | **C11 同键换 body 返回上一单** | **假成功**：客户端以为新单下成，实际没创建 | 小～中（比对 fingerprint 后 409） |
| P0 | **C12 Kafka 抖动 → 成功下单却被取消 + 库存永久丢失** | 事件终态 FAILED 无重投，且无对账兜底 | 中（重投 + 对账 + 预扣可自愈） |
| P1 | C13 券在 commit 才预占 | 并发用同券会"全部下单成功"，输家几十秒后被取消 | 中（预占提前到下单阶段） |
| P1 | C16 限流可被 `X-Forwarded-For` 绕过 | 下单链路第一道闸形同虚设，还可定向打满他人配额 | 小～中（只信可信代理的 XFF） |
| P2 | C14 quote 失败留下 QUOTED 报价单 | 靠 5 分钟定时任务兜底 | 小（补偿标志提前置位） |
| P1 | C2 乐观锁重试 / 冲突映射 409 | 多实例特有 500，用户可见 | 中 |
| P1 | C6 定时任务单实例锁 | 多副本必踩 | 中 |
| P1 | C3 outbox 认领（SKIP LOCKED / ShedLock） | **已量化：700/2655 重复投递**，C1 修好即变数据损坏 | 中 |
| P1 | C5 auth 审计 `ip` inet 类型 | `/otp/send` 直接 500，且掩盖 C4 | 小 |
| P1 | C8 确认收货 200 但静默漏子单 | 接口报成功、订单卡在待收货；多副本下 1/26 复现 | 中 |
| P1 | **E1 副本故障不转移（LB 重试 / 缓存 TTL）** | **发布期约 1/N 请求 500/超时，持续 20s+** | 小（配置）～中（加断路器） |
| P2 | A3 Flyway 并发迁移 | 生产多副本启动风险 | 中 |
| P2 | A4 7 个应用服务没有 healthcheck/readiness | 启动 45–70s 且方差大，编排无法判断就绪 | 小（补 healthcheck） |
| P2 | D1 trace 空 endpoint 丢 span | 多副本排障本就难 | 小 |
| P3 | D3 有状态层单点 | 规模上限，非本次目标 | 大 |

---

## 5. 覆盖边界：哪些没测，以及为什么

为了不把"我没测"伪装成"没问题"，这里明确列出**没有覆盖**的范围：

| 未覆盖 | 原因 | 已有的替代证据 |
|---|---|---|
| **开启鉴权后的多副本链路**（token 由 A 副本签发、B 副本校验；吊销跨副本传播） | 测试栈里**取不到 token**：auth 没有种子凭证（凭证由 account 的 `UserCredentialChangedEvent` 同步），而 `POST /api/auth/login/password` 只返回 `ResponseEntity.ok().build()`——**不回 token**；OIDC 授权码流程在 hermetic 栈里没被搭起来 | 仅**代码级**核对：`TokenVersionFilter` 每次请求读 Redis（不缓存判定）、`JwkCacheService` 是只读公钥缓存按 TTL 收敛（C10 附注栏有记录） |
| **混合版本 / 滚动升级共存**（老副本与新副本同跑、v1/v2 订单双写兼容） | 属于"升级"维度而非"多实例"维度，且需要另行构建旧版本镜像 | 未覆盖 |
| **高于 300 VU 的负载** | 本轮压到 100 / 300 VU 两档（已足够暴露 C0 的失败率随并发上升） | `make load-multi` 可用 `VUS_LEVELS` 自行加档 |
| **Redis 部分不可用**（时好时坏，导致网关本地降级与订单域 fail-closed 行为不一致） | 难以稳定构造"部分失败"，且一旦 Redis 全挂订单域会 503 fail-closed，链路直接停止 | 代码级：网关降级本地 Caffeine、订单域 `fail-on-redis-error=true` 抛 503 |

另外：**26 条待修阻塞点的修复工作不在本次范围内**（本次目标是测试与找出阻塞点，不是修）。

## 6. 一句话总结

多实例测试能力已经建成并且**能真实证伪**：E2E 全绿、跨副本不超卖这条核心承诺也成立；但并发一致性一压就红，
根因是 **跨副本 Snowflake 号碰撞（C0）**——这是纯多实例缺陷，单机测试永远发现不了，也正是本次工作的价值所在。
**下单链路是"双保险失效"**：C0 让 2–7% 的下单以 500 结束，而 C9 让这些失败无法重试（幂等键被两层各自扣住），
于是每一次碰撞都变成用户侧的死路——这两条合起来才是"下单链路在多副本下不可用"的完整解释。
再加上 C10（库存不足这类**正常业务结果也返回 500**，诱导客户端重试），三者构成一条完整的失败链：
**业务拒绝 → 500 → 客户端重试 → 幂等键已被扣 → 409 死路**。
还有一条独立于失败路径的：**C11 同键换 body 会返回 200 和上一单的标识**（新单根本没建），
在主下单接口上端到端实测复现——这是"假成功"，比报错更难发现。
异步半边也不安全：**C12 只要 Kafka 停几分钟，下单仍返回 200，随后订单被系统自动取消，
而且 1 件库存永久丢失**（预扣在 Redis、DB 预约靠 outbox 事件，事件进终态 FAILED 后无重投、无对账）。
优惠这条支路同病：**C13 券要到 commit 才预占**，所以同一张一次性券能被 5 个并发下单同时用掉，
输家几十秒后被取消（"先成功后失败"，命中率与 C12 同型）。
而顺着 C13 追下去还挖到一个一行 bug：**C15 券单的 commit 查询少写 `@Param("couponId")`，
导致"用券的下单"100% 在下单成功后被系统取消**——现有 E2E 从不传券，所以一直没人发现。
**这是全程唯一一处我改动的生产代码**（补参数注解），修完券单已能正常 commit，
C13 的裁决实验也随之跑通并确认"同一张一次性券最终只被一单占用"。
好消息是链路的**资源归还**是干净的：失败不泄漏库存、多店铺部分失败会补偿、超时释放会归还（三个实验全部通过）。
入口那道闸也不可靠：**C16 网关限流把身份取自客户端可控的 `X-Forwarded-For`**，实测轮换该头即可 200/200 全部绕过
（同身份时 26/200 会被拒）——而这一切一直没暴露，因为测试栈默认把限流关着。
而且它不是"高并发才偶发"：k6 第一次指向多副本栈，**100 并发就有 5.03% 的下单失败率，300 并发 7.49%**，
同一时间两个副本的流量却是完全均等的——**负载均衡没问题，是 ID 生成在拖后腿**。
另外两条同类缺陷是 **OTP 存内存导致跨副本登录失效（C4）** 与 **无锁 outbox/auth 发布器（C3，已量化 700/2655 重复投递）**。
可用性上还有一条：**杀掉一个副本后约一半请求失败并持续 20s 以上，没有任何故障转移（E1）**——
多副本在"正确性"和"可用性"上都还没有拿到它本该带来的收益。
其余仍待处理的是 **支付事件链路全断（C1）**、**异步回执与同步命令抢同一聚合行（C2）**、
**无单实例锁的定时任务（C6）**、**auth 审计 inet 类型导致 `/otp/send` 500（C5）**、
**7 个服务没有 healthcheck 导致启动门禁只能靠轮询（A4）** 和
**网关路由无法作为统一入口（D2）**。
