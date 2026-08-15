# 少容器下单链路峰值测试（minimal-stack peak test）

> 目标：用最少容器测出**下单链路（createTrade）**在单机上的峰值性能，并尽量消除
> 压测方法学噪音（冷启动、过渡态、库存耗尽），得到可复现的稳态数字。
> 现状结论见 [§4 实测结果](#4-实测结果2026-08-15)。

## TL;DR

- 下单链路热路径只需要 **7 个容器**（postgres/redis/kafka/nacos/order/inventory/promotion），
  相比全栈 12 容器，可去掉 gateway/auth/account/payment/product（见 §2）。
- 单机**稳态天花板 ≈ 1550-1760 RPS**（直连 order，VUS=3000-6000 平坦平台），比全栈直连
  ~1265-1300 高 **~25-35%**；甜点档 VUS=3000（~1600 RPS + p99 ~2.7s）。
- 一条命令跑完预热+扫描：`make load-min`（含「预热→丢弃首档→逐档测量」的方法学处理，§3）。
- 可选纯同步探针 `make load-min-nokafka`（6 容器，无 broker + outbox 发布器闲置，§6）。

## 1. 为什么少容器能测出更高峰值

单机资源（CPU/内存）是固定的。全栈 12 容器里，gateway/auth/account/payment/product 都
**不在 createTrade 同步路径上**，却仍占内存、吃 CPU（各自的 JIT/GC/调度器/Kafka consumer），
与热路径争抢。少跑它们既释放资源给 order/inventory/promotion/PG，也消掉后台干扰。

## 2. 下单链路（createTrade）热路径依赖

```
gateway(可选) → order → { promotion.quote()  , inventory.preDeductRedisOnly() }
                          + PostgreSQL(同事务 trade/shop_order/outbox)
                          + Redis(幂等 SETNX + 库存预占)
                          + Kafka(仅 outbox 异步发布，不在同步响应路径)
```

| 容器 | 是否热路径 | 说明 |
|------|-----------|------|
| order | ✅ 必留 | 被测服务（直连 :28080） |
| inventory | ✅ 必留 | `preDeductRedisOnly`（Redis-only，同步） |
| promotion | ✅ 必留 | `quote`（同步，含 DB 写 `checkout_quote`） |
| postgres | ✅ 必留 | 同事务写入 |
| redis | ✅ 必留 | 幂等 SETNX + 库存 |
| kafka | ✅ 保留 | outbox 异步发布；掉线不阻塞同步响应 |
| nacos | ✅ 保留 | Feign 服务发现（order→inventory/promotion） |
| gateway | ❌ 可去 | 用 `order_create_direct.js` 直连，省 ~13-15% 转发损耗 |
| auth | ❌ 可去 | 测试环境资源服务器已禁用（`TINYSTORE_SECURITY_RESOURCESERVER_ENABLED=false`） |
| account | ❌ 可去 | createTrade 不调用 |
| payment | ❌ 可去 | 仅 OrderTimeoutScheduler 使用，非下单路径 |
| product | ❌ 可去 | product cache 关闭，payload 直接携带 productId/skuId |

**结论：12 → 7 容器**（postgres/redis/kafka/nacos/order/inventory/promotion）。

## 3. 工具与使用方法

### 3.1 一键压测

```bash
# 默认：预热 = 扫描起始档（丢弃）→ 首个测量档为过渡态（自动丢弃）→
#       VUS=1000/2000/3000/4000/5000，每档 30s，每档独立 SKU（stock=50 万）
make load-min

# 自定义：第一档（=预热档）会被丢弃，从第二档起才是稳态测量
VUS_LEVELS="2000 3000 4000 5000 6000" DURATION=60s WARMUP_LEVEL=2000 make load-min
```

流程：`build` → 启动最小栈 → 等 postgres/order/inventory/promotion 健康 → 预热一档
（JIT/Feign/连接池热身后丢弃）→ 按档位 seed `inventory_stock`（每档独立 SKU、stock 50 万，
避免库存耗尽污染）→ `k6 run --quiet order_create_direct.js` 直连 order:28080 → 结束后
`down -v`（`trap` 保证清理）。

### 3.2 方法学要点（为什么要这么设计）

- **丢弃首个测量档**：预热后的第一个测量档无论哪个 VUS 都稳定复现偏低 40-60%
  （实测 VUS=2000→789/833/871/907、VUS=3000→1095/1173，后续同档立即回到 1580-1760）。
  曾试过 settle 排空 outbox 积压、Hikari `minimum-idle=maximum` 均不能消除——判断为
  「预热→过渡→首档」的栈级冷启动假象（线程池/连接池/缓存/JIT 综合），属测量噪音，直接丢弃。
- **预热档 = 扫描起始档**（可 `WARMUP_LEVEL` 覆盖）：让首档测量面对一个已被同等并发热过的系统。
- **每档独立 SKU**：`SKU-perf-<VUS>`，库存 50 万，60s×8000VUS 也不耗尽；避免全栈首测时
  `SKU_A` 库存 1 万被瞬间打光导致 `STOCK_LACK` 污染（见 08-02 报告 §1）。
- **NO DATA 自动重试一次**：`order_create_direct.js` 从 `https://jslib.k6.io/...` 远程 import
  `randomString`，瞬时 TLS 超时会整档作废；脚本检测 `NO DATA` 后 5s 重试一次。

### 3.3 指标解析

`k6 run --quiet` 让 stdout 只剩 handleSummary 的 JSON（`--quiet` 抑制 banner/进度），由
`perf/k6/k6_summary.py` 解析出 `RPS / avg / p95 / p99 / fail` 一行输出。

- 时延单位 **ms**（k6 内部时间单位，实测 `Trend.add(1000)` → avg=2000 确认）。
- p99 取自 handleSummary 数据；k6 v2.0 的 `--summary-export` **不含打 tag 子指标、也没有 p99**，
  所以不用它做最终输出（仅留作 /tmp 复查）。
- 每档原始 JSON/err 留在 `/tmp/tinystore-perf-<lvl>.{json,err,export.json}`。

### 3.4 调参速查

| 变量 | 默认 | 说明 |
|------|------|------|
| `VUS_LEVELS` | `1000 2000 3000 4000 5000` | 扫描档位（首个会被丢弃） |
| `DURATION` | `30s` | 每档时长 |
| `WARMUP_LEVEL` | 扫描起始档 | 预热并发数（丢弃） |
| `STOCK_PER_SKU` | `500000` | 每档独立 SKU 的库存 |
| `BASE_URL` | `http://localhost:28080` | 直连 order（k6 脚本内） |

## 4. 实测结果（2026-08-15，最小栈，直连 order，60s/档）

### 4.1 稳态平台（丢弃首档后的连续测量档）

| VUS | RPS 区间 | avg(ms) | p95(ms) | p99(ms) | 备注 |
|-----|---------|---------|---------|---------|------|
| 3000 | 1552-1629 | 1802-1896 | 2397-2466 | 2749-2815 | 甜点档（峰值且延迟最优） |
| 4000 | 1545-1763 | 2216-2523 | 2884-3131 | 3268-3585 | 平台期 |
| 5000 | 1546-1753 | 2776-3124 | 3596-3779 | 3930-4323 | 平台期 |
| 6000 | 1556-1615 | 3585-3692 | 4416-4451 | 4806-4892 | 平台期上沿 |

- **稳态天花板 ≈ 1550-1760 RPS**（多次运行取区间，受预热充分度影响）。
- 延迟随 VUS 线性增长（avg 1.8s→3.7s）而 RPS 保持平台 → 系统在平台期是**延迟受限**
  而非资源受限；上限由同步链本身决定（order 同事务 PG 写 + Feign 逐跳 + Redis）。
- 相比全栈直连 ~1265-1300（08-02 报告），最小栈提升 **~25-35%**；相比经网关 ~1100 提升更多。

### 4.2 首档过渡态（各次实测，供参考）

| VUS | RPS | 说明 |
|-----|-----|------|
| 2000 | 789 / 833 / 871 / 907 | 均为「预热后第一个测量档」 |
| 3000 | 1095 / 1173 | 同上 |

这些值与预热档位、是否 settle、Hikari 配置无关——确认是过渡态测量假象，工具已自动丢弃。

### 4.3 饱和行为（早期 08-12 扫描，warmup 500）

| VUS | RPS | avg(ms) | p99(ms) |
|-----|-----|---------|---------|
| 6000 | 1424 | 4054 | 5821 |
| 7000 | 1335 | 4937 | 6971 |
| 8000 | 1314 | 5711 | 8445 |

超过平台后 RPS 回落、延迟线性恶化——正常排队饱和曲线，非缺陷；峰值取平台期即可。

## 5. 对比全栈（12 容器）

| | 全栈 `make load` | 最小栈 `make load-min` |
|---|---|---|
| 容器数 | 12 | 7 |
| 入口 | gateway :8080 | order 直连 :28080 |
| 覆盖范围 | 全链路（含网关/账号/商品/支付） | 下单链路本身 |
| 稳态峰值 | ~1100（经网关）/ ~1265-1300（直连） | ~1550-1760（直连） |
| 空闲容器后台开销 | 5 个非热路径容器的 JIT/GC/调度器/消费者 | 无 |

> 注意：两者端口同段（28080/13000/1200/5433/6380/9093/8849），不可同时运行；
> 与 `make load`/`make load-matrix` 一样，目标结束时自动 `down -v` 清理。

## 6. 进一步瘦身（可选 A/B 探针，非默认）

- **纯同步探针 `make load-min-nokafka`**（6 容器，无 kafka）：DB 争抢的真正来源是
  **outbox 发布器对 outbox 表的轮询/批量标记**（不是 broker 本身），所以探针同时做了三件事：
  Kafka bootstrap 指向可解析但无监听的 `127.0.0.1:9092`（否则 `PromotionAckConsumer` 等
  `@KafkaListener` 启动时解析 `kafka:29092` 失败直接起不来）、
  `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`（consumer 不启动、无重试风暴）、
  `ORDER_OUTBOX_POLL_INTERVAL=86400000`（发布器首轮后闲置，不再抢 DB 连接）。
  代价：异步促销/库存链路不闭环、outbox 事件累积——仅适合“纯同步峰值”探针。
- **去掉 nacos**（→ 6 容器）：需要给 `@FeignClient(name=..., url="${...}")` 配静态 URL 的代码改动，
  nacos 本身很轻，收益边际，不建议为压测引入代码改动。

## 7. 已知注意事项

- **远程 jslib 依赖**：k6 脚本 import `https://jslib.k6.io/k6-utils/1.4.0/index.js`，离线/弱网会
  init 失败（工具已自动重试一次）。要彻底消除，可把该文件 vendor 到 `perf/k6/vendor/` 并改本地 import。
- **端口冲突**：最小栈端口与全栈同段，两者不能同时运行。
- **环境**：以上数据在 16 核 / 29G 单机、docker-compose 栈上测得；换机器需重测。
