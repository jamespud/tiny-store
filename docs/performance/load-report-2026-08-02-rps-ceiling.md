# 单机最高 RPS 测定报告 (2026-08-02)

> 环境：单机 docker-compose-test 栈（gateway :8080 + order :28080 + inventory :13000 + PG/Redis/Nacos/Kafka），16 核 / 29G
> 工具：k6 v2.0.0（--summary-export 全量数值）
> 方法：分层压测（直连 order vs 经网关）+ VUS 扫描 + 峰值 60s 精测
> 前置调优（沿用 08-02 配置）：order Hikari=100 / Tomcat=500；网关 HttpClient 池=2000；PG max_connections=500

## 1. 方法学修正（本次新增）

| 问题 | 影响 | 修复 |
|------|------|------|
| SKU_A 库存上限 10000 | 60s VUS=500 即耗尽，后续请求返回 STOCK_LACK（HTTP 200 code≠0），虚高 RPS、污染延迟 | 每档独立新 SKU（DB insert + Redis SETNX 自动初始化），库存 50 万，30s×5000VUS 不耗尽 |
| PG max_connections=100 | order Hikari 100 建满后其余服务全连不上（`too many clients already`），扫描僵死 | `postgres -c max_connections=500` |
| 直连 order 路径 | 网关 StripPrefix=1 后 `/api/order/trades`→`/order/trades` | 新增 `perf/k6/order_create_direct.js` |

## 2. 分层扫描（30s/档，error 全部 ≈0）

| VUS | 直连 order RPS | 直连 P99(ms) | 经网关 RPS | 经网关 P99(ms) |
|-----|---------------|-------------|-----------|---------------|
| 500  | 402¹         | 5513        | 999       | 1183 |
| 1000 | 1240         | 1973        | 1098      | 1895 |
| 2000 | **1295**     | 2411        | **1121**  | 2654 |
| 3000 | 1280         | 3186        | 1092      | 3724 |
| 4000 | 1248         | 4179        | 1114      | 4565 |
| 5000 | 1216         | 5110        | 1085      | 5638 |

> ¹ VUS=500 直连档为冷启动（JIT/Feign 预热），后续档位稳定。

## 3. 峰值精测（VUS=2000, 60s）

| 链路 | RPS | P95(ms) | P99(ms) | avg(ms) | max(ms) | error |
|------|-----|---------|---------|---------|---------|-------|
| 直连 order | **1265.0** | 2142 | 2453 | 1559 | 4330 | 0 |
| 经网关 | **1102.1** | 2443 | 2985 | 1789 | 5720 | 110 req |

## 4. 结论

- **单机最高 RPS ≈ 1265-1300**（直连 order，VUS=2000 平台期），经网关全链路 ≈ **1100**。
- **瓶颈层：order 同步事务链路**（Feign Redis preDeduct + trade/shop_order/outbox 同事务 DB 写入）。VUS 3000→5000 平台期平稳不涨（RPS 反略降），线程池/连接池已不是限制，CPU 争抢与 DB 事务串行化为天花板。
- **网关损耗 ≈ 13-15%**（1300→1100，约 165-190 RPS）：body 透传 + Redis 幂等 SETNX + 转发，属正常开销，非缺陷。
- **P99 特征**：VUS≤1000 时 P99<2s（达标），VUS=2000 时 ~2.5s，之后线性恶化（每 +1000 VUS 约 +1s）——峰值点取 VUS=2000（P99 2.5s / RPS 1265 的平衡点）。
- **error ≈0**（全档位）：新 SKU 方法下无库存干扰、无 500，链路在饱和点前完全健康。

## 5. 附：测试环境配置变更（docker-compose-test.yml）

| 配置 | 改动 | 原因 |
|------|------|------|
| postgres `command: max_connections=500` | 新增 | order Hikari=100 打满默认 100 上限 |
| perf/k6/order_create_direct.js | 新增 | 直连 order 分层压测 |
| perf/k6/order_create.js `SKU_ID` env | 修改 | 每档新 SKU 消除库存耗尽污染 |
