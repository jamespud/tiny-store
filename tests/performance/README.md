# Performance Testing Module

## 目的

本模块提供高并发下的一致性与性能测试，独立于现有的unit/it/e2e测试体系。

主要测试场景：
1. **下单幂等一致性** (OrderCreateIdempotencyConsistencyIT)
2. **库存不超卖一致性** (InventoryNoOversellConsistencyIT)
3. **负载性能基线** (k6 scripts)

## 快速开始

### 1. 运行一致性测试（JUnit）

```bash
# 完整流程（启动环境 + 执行测试 + 清理）
make consistency
```

内部步骤：
- 启动 docker-compose-test 环境
- 等待 gateway + 下游服务就绪
- 执行 200 并发 JUnit 测试（含数据库强断言）
- 自动清理（down -v）

### 2. 运行负载测试（k6）

```bash
# 前提：安装 k6
# macOS:   brew install k6
# Linux:   sudo apt install k6
# Windows: choco install k6

# 完整流程（启动环境 + k6 压测 + 清理）
make load
```

内部步骤：
- 启动 docker-compose-test 环境
- 等待服务就绪
- 执行 k6 负载脚本（order_create.js）
- 输出 p95/p99 延迟、RPS、错误率
- 自动清理

## 模块结构

```
tests/performance/
├── pom.xml                                     # Maven 配置（默认 skipPerfTests=true）
└── src/test/java/com/github/spud/tinystore/tests/performance/
    ├── OrderCreateIdempotencyConsistencyIT.java   # 幂等测试（200 并发同一 tradeId）
    ├── InventoryNoOversellConsistencyIT.java      # 不超卖测试（200 并发竞争同一 SKU）
    └── support/
        ├── GatewayClient.java                      # HTTP 客户端（原生 Java 11+ HttpClient）
        └── PostgresClient.java                     # JDBC 客户端（直接查询 DB 做强断言）

perf/k6/
├── order_create.js                             # k6 脚本：压测 POST /api/order/trades
└── order_create_and_pay.js                     # k6 脚本：create→pay 完整链路
```

## 测试详解

### OrderCreateIdempotencyConsistencyIT

**场景**：200 个并发请求同时使用相同的 `tradeId` 和 `Idempotency-Key` 下单

**断言（强，基于 DB）**：
- `tinystore_order.trade` 表中 `trade_id = ?` 的记录数 = 1
- `tinystore_order.shop_order` 表中 `trade_id = ?` 的记录数 = 1
- `tinystore_inventory.inventory_reservation` 表中 `trade_id = ? AND status='RESERVED'` 的记录数 = 1
- `inventory_stock.reserved_quantity = 1`（单 SKU 场景）

**断言（弱，基于 HTTP）**：
- 不允许出现 5xx 错误
- 所有成功响应的 `paymentIntentId` 应一致（幂等语义）

### InventoryNoOversellConsistencyIT

**场景**：200 个并发请求使用不同 `tradeId`，竞争同一 SKU（SHOP_A/SKU_A，初始库存 10000）

**断言（强，基于 DB）**：
- `inventory_stock.reserved_quantity <= 10000`（不超卖核心不变式）
- `COUNT(trade)` = 成功订单数（准确匹配）
- `reserved_quantity` 增量 = 成功订单数（一致性）
- `COUNT(inventory_reservation WHERE status='RESERVED')` = 成功订单数

**断言（弱，基于 HTTP）**：
- 成功响应数量 = 200（库存充足，全部成功）
- 无"库存不足"错误

### k6 Load Tests

**order_create.js**：
- 压测 POST /api/order/trades
- 每个请求生成唯一 tradeId
- 默认参数：10 VUs，5秒持续时间（约50请求）
- 输出：p95/p99 延迟、RPS、错误率

**重要说明**：
- 初始库存只有100个单位（SKU_A）
- 当请求数超过库存容量时，会出现高错误率（"Inventory pre-occupy failed"）
- **这是预期行为**，验证了不超卖机制正常工作
- 如需测试高吞吐性能，建议：
  - 降低并发/持续时间（默认配置已调整）
  - 或在每轮测试前重置数据库（`docker compose down -v && up`）

**InventoryNoOversellConsistencyIT**：
- 压测完整链路：create→pay
- 测试端到端性能
- 默认参数：20 VUs，30秒
- 输出：链路成功率、端到端 p95/p99

**关于库存容量**：
- 初始库存：**1000000个单位**（SKU_A/SKU_B）
- 足以支持大规模并发测试
- 一致性测试（JUnit）使用 200 并发，仅消耗200库存
- k6 默认参数（50 VUs × 30s）预计消耗 1000-1500 库存

## 配置参数

### Maven 系统属性（通过 `-Pperf` profile 启用）

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `gateway.base.url` | `http://localhost:8080` | Gateway 地址 |
| `perf.concurrency` | `200` | 并发线程数 |
| `pg.url` | `jdbc:postgresql://localhost:5433/tinystore` | PostgreSQL 连接 |
| `pg.user` | `postgres` | 数据库用户 |
| `pg.password` | `postgres` | 数据库密码 |
| `perf.timeoutSeconds` | `120` | 测试超时时间 |

### k6 环境变量

| 变量 | 默认值                                  | 说明 |
|------|--------------------------------------|------|
| `BASE_URL` | `http://localhost:8080`              | Gateway 地址 |
| `VUS` | `100` (order_create)<br>`20` (chain) | 虚拟用户数 |
| `DURATION` | `60s`                                | 测试持续时间 |

**并发度参考标准**：
- **低并发测试**：10-20 VUs，约 10-50 RPS
- **中等并发**：50-100 VUs，约 100-500 RPS
- **高并发**：200-500 VUs，约 1000-3000 RPS
- **极限压测**：1000+ VUs，5000+ RPS

**关于网关限流**：
- 默认情况下，网关 `IpRateLimiterFilter` 会按 IP 维度限流（配置见 `gateway-routes.yml` 中的 `policies.rateLimit`）
- 在 `docker-compose-test.yml` 环境中，限流已被禁用（`GATEWAY_RATE_LIMIT_ENABLED=false`），以便测试系统极限吞吐
- 若需恢复限流保护，修改 compose 文件中该环境变量为 `true` 或移除该行
- 禁用限流后，429 错误应消失；若仍出现 5xx，瓶颈来自下游服务或数据库

示例：
```bash
cd perf/k6

# 默认（中等并发）
k6 run order_create.js

# 高并发压测（预计 1000+ RPS）
VUS=200 DURATION=60s k6 run order_create.js

# 极限压测（预计 3000+ RPS，需强悍硬件）
VUS=500 DURATION=120s k6 run order_create.js

# 注意：高并发测试会快速消耗库存（10000单位）
# 500 VUs × 120s 理论可产生 30000+ 请求
# 实际受系统吞吐能力限制
```

## CI 集成

### 默认行为

- **skipPerfTests=true**：默认跳过性能测试，不影响现有 CI 流程
- `make test` 不会执行性能测试

### 手动启用

```bash
# 方式1：通过 Makefile 目标（推荐）
make consistency  # 自动管理环境生命周期

# 方式2：通过 Maven profile
# 前提：手动启动 docker-compose-test 环境
./mvnw -pl tests/performance test -Pperf
```

### Nightly CI 建议

在 CI 中添加独立的 nightly job：
```yaml
# .gitlab-ci.yml 或 .github/workflows/nightly.yml
nightly-consistency:
  script:
    - make consistency
  only:
    - schedules
```

## 依赖

### 运行时依赖

- Docker + Docker Compose（用于 test 环境）
- PostgreSQL 18.1（通过 compose 自动启动）
- Java 17+
- Maven 3.9+

### 可选依赖

- k6（仅用于 `make load`）
  - macOS: `brew install k6`
  - Linux: `sudo apt install k6`
  - Windows: `choco install k6`

## 故障排查

### 问题1：测试超时

**症状**：`CountDownLatch.await()` 返回 `false`

**排查**：
```bash
# 检查环境是否正常启动
docker compose -f docker/docker-compose-test.yml ps

# 查看 gateway 日志
docker compose -f docker/docker-compose-test.yml logs gateway

# 手动测试健康检查
curl http://localhost:8080/actuator/health
curl http://localhost:8080/internal/health/order
```

### 问题2：DB 连接失败

**症状**：`Failed to connect to Postgres`

**排查**：
```bash
# 确认 Postgres 端口
docker compose -f docker/docker-compose-test.yml ps postgres

# 尝试连接
psql -h localhost -p 5433 -U postgres -d tinystore
# 密码：postgres
```

### 问题3：幂等测试失败（多条记录）

**症状**：`Expected: 1, Actual: 2`

**分析**：
- 幂等逻辑可能存在 race condition
- 查看 `inventory_reservation` 表的 `operation_id` 是否重复

**调试 SQL**：
```sql
SELECT * FROM tinystore_order.trade WHERE trade_id = 'perf-idempotency-xxx';
SELECT * FROM tinys

**症状**：大量 500 错误 "Inventory pre-occupy failed"

**可能原因**：
1. **库存耗尽**：当前库存 10000 单位，高并发长时间测试可能消耗完
2. **系统瓶颈**：数据库连接池、线程池等资源耗尽
3. **业务限流**：触发业务层的频率限制

**诊断方法**：
```bash
# 查看当前库存
psql -h localhost -p 5433 -U postgres -d tinystore -c \
  "SELECT * FROM tinystore_inventory.inventory_stock WHERE sku_id='SKU_A';"

# 查看预占记录数
psql -h localhost -p 5433 -U postgres -d tinystore -c \
  "SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation WHERE status='RESERVED';"
```

**解决方案**：
```bash
# 方案1：使用默认参数（中等并发）
make load  # 50 VUs × 30s ≈ 1000-1500 请求

# 方案2：测试前重置数据库（恢复10000库存）
docker compose -f docker/docker-compose-test.yml down -v
docker compose -f docker/docker-compose-test.yml up -d
# 等待服务就绪后运行 k6

# 方案3：降低并发度
cd perf/k6
VUS=20 DURATION=310 VUs × 5s ≈ 50 请求

# 方案2：测试前重置数据库
docker compose -f docker/docker-compose-test.yml down -v
docker compose -f docker/docker-compose-test.yml up -d
# 等待服务就绪后运行 k6

# 方案3：自定义参数（低并发）
cd perf/k6
VUS=5 DURATION=10s k6 run order_create.js
```析**：
- SELECT FOR UPDATE 可能未正确锁定
- 乐观锁 `@Version` 可能冲突处理不当

**调试 SQL**：
```sql
SELECT * FROM tinystore_inventory.inventory_stock WHERE shop_id = 'SHOP_A' AND sku_id = 'SKU_A';
SELECT COUNT(*) FROM tinystore_inventory.inventory_reservation 
WHERE shop_id = 'SHOP_A' AND sku_id = 'SKU_A' AND status = 'RESERVED';
```

## 扩展

### 添加新的一致性测试

1. 创建新的测试类（命名规范：`*ConsistencyIT.java`）
2. 继承类似的并发执行模式：
   ```java
   ExecutorService executor = Executors.newFixedThreadPool(concurrency);
   CountDownLatch startLatch = new CountDownLatch(1);
   CountDownLatch doneLatch = new CountDownLatch(concurrency);
   // ...
   startLatch.countDown(); // 同时起跑
   doneLatch.await(timeoutSeconds, TimeUnit.SECONDS);
   ```
3. 使用 `PostgresClient` 添加强断言

### 添加新的 k6 脚本

1. 在 `perf/k6/` 目录创建新脚本（命名规范：`*.js`）
2. 参考 `order_create.js` 的 metrics 结构
3. 在 `Makefile` 的 `load` 目标中添加新脚本执行

## 参考

- [k6 Documentation](https://k6.io/docs/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/documentation/)
