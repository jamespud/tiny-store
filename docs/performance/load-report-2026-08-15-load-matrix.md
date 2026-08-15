# 全栈负载矩阵报告 (2026-08-15, load-matrix 修复后干净重跑)

> 环境：单机 docker-compose-test 栈（gateway :8080 + order/inventory/promotion + PG/Redis/Nacos/Kafka），16 核 / 29G
> 工具：k6（`--quiet` + handleSummary JSON → `k6_summary.py`，同 `make load-min` 路径），逐档 60s，逐档独立 SKU（`SKU-matrix-<vus>`，stock 50 万）
> 对比基线：[2026-08-02 报告](./load-report-2026-08-02.md)（RPS ~1150，VUS=10000 饱和）

## 1. 本次修复（commit 02f32ea）

| 项 | 旧 | 新 |
|----|----|----|
| `load-matrix` 职责 | 混跑 3 组 JUnit 一致性 IT + k6 | **纯 k6 多档负载扫描**（一致性 IT 拆到 `make load-matrix-it`） |
| 结果提取 | `grep http_req_duration|http_reqs|...` | `--quiet` + `k6_summary.py` 解析 JSON（每档一行 RPS/avg/p95/p99/fail） |
| 结果清晰度 | grep 只匹配到指标名头行，数值行被丢弃（日志里只有空壳键名） | 每档一行量化结果 + 末尾汇总表 + `THRESHOLD-CROSSED` 状态 |
| 库存 | 共用 `SKU_A`（stock 1 万，易被打光污染后续档） | 逐档独立 `SKU-matrix-<vus>`（stock 50 万，`ON CONFLICT DO NOTHING` 幂等） |
| 中断语义 | k6 阈值触发（exit 99）会让矩阵提前终止 | 逐档捕获 exit code，仅标记 `THRESHOLD-CROSSED`，矩阵跑完 |

## 2. 实测结果（k6 全栈经网关，60s/档）

| VUS | RPS | avg(ms) | p95(ms) | p99(ms) | fail% | 状态 |
|-----|-----|---------|---------|---------|-------|------|
| 500 | 700.1 | 712 | 2218 | 4463 | 0.00 | thresholds ok |
| 1000 | 1412.4 | 703 | 1217 | 1580 | 0.01 | thresholds ok |
| 5000 | 1411.6 | 3424 | 4089 | 4505 | 0.44 | thresholds ok |
| 10000 | 1040.8 | 6668 | 7147 | 30420 | 4.20 | **THRESHOLD-CROSSED** |

## 3. 关键结论

- **RPS 天花板 ≈ 1410-1412（全栈经网关）**，平台在 VUS=1000~5000 之间；相比 08-02 报告 ~1150 提升约 +23%（同期 promotion 异步化等调优累积）。
- **甜点档 VUS=1000**：RPS 1412 + p99 1.58s，吞吐与延迟兼顾最佳。
- **VUS=500 为首档过渡态**：RPS 偏低（700）且 p95 2.2s，与 `load-min` 方法学中"预热后首档偏低 40-60%"现象一致，不作为稳态值。
- **VUS=10000 过饱和**：RPS 回落至 1040、p99 劣化到 30.4s、fail 4.2%，确认单机栈在 10000 并发时的下游链条瓶颈（与 08-02 §4 结论一致）。
- **`THRESHOLD-CROSSED` 正确标记过饱和档**且不中断整轮矩阵，fail% 4.2% 主要由 p99 30s 超时请求构成。

## 4. 工具与方法（复用 load-min 成熟路径）

- `make load-matrix`：`build` → 起 compose-test 栈 → 等 gateway + Nacos 注册 → 逐档 seed `SKU-matrix-<vus>` → `perf/k6/run_matrix.sh`（`k6 run --quiet` + `k6_summary.py`）→ 自动 `down -v`。
- `make load-matrix-it`：原一致性矩阵（超卖/幂等/confirm 多档并发 + DB 强断言），与负载矩阵解耦。
- `run_matrix.sh` 支持 `VUS_LEVELS` / `DURATION` / `BASE_URL` / `SKU_ID` / `SCRIPT` 覆盖，含 NO DATA 自动重试一次。
