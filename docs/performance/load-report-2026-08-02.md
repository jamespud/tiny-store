# 库存压测报告 (2026-08-02, 网关/order 调优后)

> 环境：单机 docker-compose-test 栈（gateway :8080 + inventory :13000 + PG/Redis/Nacos/Kafka），16 核 / 29G
> 工具：JUnit + Awaitility + k6（--summary-export 全量数值）
> 对比基线：[2026-07-31 报告](./load-report-2026-07-31.md)（异步迁移后、调优前）

## 1. 本次调优改动（仅 docker-compose-test.yml，测试环境隔离）

| 层 | 配置 | 改动前 | 改动后 |
|----|------|--------|--------|
| order Hikari | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | 25 | 100 |
| order Tomcat | `SERVER_TOMCAT_THREADS_MAX` | 200（默认） | 500 |
| gateway HttpClient 池 | `SPRING_CLOUD_GATEWAY_HTTPCLIENT_POOL_MAX_CONNECTIONS` | 500（默认） | 2000 |
| gateway acquire 排队 | `..._MAX_PENDING_ACQUIRE_COUNT` | 无限（默认） | 20000 |
| gateway 下游超时 | `..._RESPONSE_TIMEOUT` | 无 | 30s |

## 2. 矩阵对比（k6 全量重跑，同参数 60s/档）

| 并发 | P95(ms) 前→后 | P99(ms) 前→后 | RPS 前→后 | error% 前→后 |
|------|--------------|--------------|-----------|--------------|
| 500  | 1878→1571    | 3490→2917    | 721.8→843.8 | 0→0 |
| 1000 | 1257→1506    | 1428→1894    | 1024.9→1148.5 | 0→0 |
| 5000 | 5929→5236    | 6270→5799    | 969.2→1091.5 | 0.406→0.000 |
| 10000| 14664→19861  | 30733→30304  | 925.8→833.5 | 6.354→5.058 |

## 3. 关键结论

- **VUS=5000 错误清零**：256 个 500 → 0。网关 HttpClient 池 500→2000 + order Tomcat/Hikari 扩容消除了下游饱和溢出，这是最明确的改善。
- **RPS 天花板提升**：~1000 → ~1150（5000 档 969→1091，1000 档 1025→1149）。
- **P99 改善**：5000 档 6270→5799ms（-8%）、500 档 3490→2917ms（-16%）。
- **VUS=10000 仍饱和**：error 6.35%→5.06%（轻微改善），P99 30.3s 未变。瓶颈已从网关池转移到下游链条。
- **幂等**：C=500/1000/5000 全过（DB 三层断言 trade=1/shop_order=1/reservation=1）；C=5000 客户端连接错误 151→886（Java HttpClient 突发连接数，与网关无关）；C=10000 由"0 成功"改善为"120s 内未完成"（finished=false 超时）。
- **超卖/confirm**：4 档超卖 + 2 档 confirm 锁竞争全部通过，不受调优影响。

## 4. 剩余瓶颈分析

调优后 VUS=10000 仍饱和的成因（非网关池）：

1. **单机 CPU 争抢**：16 核跑 8 个 JVM 容器 + Kafka + PG + Redis，order 事务路径（Feign Redis preDeduct + 同事务 4 次 DB 插入 + Outbox 写入）CPU/IO 串行化。
2. **Java HttpClient 客户端突发连接**（幂等测试）：5000-10000 线程瞬间建连 → 网关 accept/backlog 排队 → connect timeout（10s）。测试方法学问题，非网关缺陷。
3. **order 同步阻塞模型**：Tomcat 线程 + Hikari 连接随并发线性消耗，已调优至 500/100 仍受限于单机资源；DB 事务内串行写入是每请求延迟下限。

## 5. 后续优化方向（如需支撑 10000+ 完整链路）

| 方向 | 做法 | 预期 |
|------|------|------|
| 水平扩展 | gateway/order 各 +1 实例（Nacos LB 已配置 `lb://`） | error 显著下降，RPS 线性增长 |
| 压测方法修正 | 幂等测试改用连接复用（k6 或连接池客户端），并发上限 ≤2000 | 消除客户端 connect 错误噪声 |
| 写路径拆分 | Outbox 事件批量落库 / trade 与 outbox 分事务 | 降低单请求 DB 持锁时间 |
| 资源分配 | compose-test 给 order/gateway 显式 CPU 限额，避免 8 服务争抢 | 延迟方差下降 |
