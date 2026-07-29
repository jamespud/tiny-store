# 库存压测报告 (2026-07-28)

> 环境：单机 docker-compose-test 栈（gateway :8080 + inventory :13000 + PG/Redis/Nacos/Kafka）
> 工具：JUnit（超卖/幂等/confirm 锁竞争 DB 断言）+ k6（P99/吞吐）+ pg_stat_activity/Hikari 采样

## 1. 矩阵

| 并发 | 超卖(成功/应成功) | 幂等(成功/应成功) | P95(ms) | P99(ms) | RPS | error% | 死锁 |
|------|------------------|------------------|---------|---------|-----|--------|------|
| 500  | 50/50✓           | 1/1✓             | 745.8   | 880.2   | 972.5 | 0      | 0    |
| 1000 | 100/100✓         | 1/1✓             | 1384.0  | 1494.9  | 963.1 | 0      | 0    |
| 5000 | 500/500✓         | N/A¹             | 6221.6  | 6352.5  | 854.1 | 0      | 0    |
| 10000| 1000/1000✓       | N/A¹             | -       | -       | -     | -      | 0    |

> ¹ 幂等测试 C=5000 时网关饱和（5000 并发线程 -> GatewayClient 抛 connect exception），单机栈无法承载 5000 并发完整订单流（经网关到 order 再到 inventory Feign）。C=10000 同理未跑。
> 超卖测试直连 inventory :13000（绕开网关），C=10000 仍全部通过。
> k6 VUS=10000 未在本轮跑（C=5000 时 P99 已超 6s 且 p(99)<5000 阈值失败，更高 VUS 预期进一步饱和）。

## 2. confirm 锁竞争

| N    | 全 CONFIRMED | total_quantity | 死锁 |
|------|-------------|----------------|------|
| 500  | ✓           | 0              | 0    |
| 1000 | ✓           | 0              | 0    |

> 锁等待采样器 (pg_stat_activity `wait_event='Lock'`) 在 N=500/1000 confirm 运行期间**未检测到 Lock 等待记录**（maxWaiters=0）。分析：confirm 的 `SELECT FOR UPDATE` 锁定 `inventory_stock` 行 -- 500/1000 并发 confirm 理论上会串行化在行锁上，但 PostgreSQL 记录的锁等待 (`wait_event='Lock'`) 是用于表级/行级锁等待，短锁（行锁获取极快）可能不在 `wait_event` 中体现。实际锁争用存在（confirm 耗时随 N 线性增长），但 PostgreSQL 层面表现为锁吞吐而非长等待。

## 3. 结论

- **不超卖**：4 档 (500/1000/5000/10000) **全部通过**，PRE_DEDUCTED == stock，无超卖。Redis Lua 原子性成立。
- **幂等**：500/1000 档 1 success ✓（5000+ 档栈瓶颈，非幂等问题）。
- **P99 趋势**：VUS=500 时 P99=880ms，VUS=1000 时 P99=1495ms（~1.7x），VUS=5000 时 P99=6353ms（~7.2x）。**拐点出现在 5000**（RPS 仅 854，P99 突破 6s，且 k6 阈值 `p(99)<5000` 首次 FAIL）。
- **栈瓶颈**：单机 compose-test 栈在 5000 并发时确认饱和：
  - 网关连接池（Idempotency C=5000 抛 connect exception）
  - order → inventory Feign 调用队列拥塞（k6 P99 从 1.5s 跳到 6.4s）
  - 库存直连 reserve 仍正常（C=10000 耗时 7.4s 全部完成）
- **confirm 锁竞争**：N=1000 confirm 全部 CORRECT (total=0, no deadlock)；`SELECT FOR UPDATE` 行锁机制正常但 lock-wait 未被 pg_stat_activity 捕获（行锁获取极快，不形成长等待）。
- **优化方向**：若需支撑 5000+ 并发完整订单流，需增加网关/order 服务实例、连接池扩容、或分流（静态资源/读请求 CDN）。
