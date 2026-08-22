# E1–E4 Demo→可上线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 roadmap 的 E1–E4：扩展 E2E 覆盖、引入分布式链路追踪、新增生产配置与密钥管理、外置 e2e 种子数据，使系统向“可上线”更进一步。

**Architecture:** 全部改动限定在现有 8 个 Maven 模块 + `tests/api` 黑盒层：
- **E3** 配置层：各模块新增 `application-prod.yml`（env 驱动、无硬编码密钥）；不安全 `.dev.tinystore.com` 默认改为 env 占位；DB 密码改 `${DB_PASSWORD:}` 风格。
- **E4** 种子外置：把 Flyway 核心迁移里的 `V5__e2e_seed_stock.sql`（inventory）与 `V7__e2e_seed_skus.sql`（product）移出 `db/migration`，改为独立的 E2E 侧直连 Postgres 的种子夹具。
- **E2** 追踪：共享 `tinystore-library-infrastructure` 引入 `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`，各服务自动获得追踪；配置采样率与导出 endpoint（env 驱动）。
- **E1** E2E：在 `tests/api` 新增多商品、多店铺拆单、售后多态、取消等黑盒用例；复用 E2E 直连 Postgres 做强断言。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Cloud 2025, Micrometer Tracing (Brave), Zipkin, JUnit 5, TestRestTemplate, Postgres (compose-test 宿主机 5433), MySQL 无。

---

## E4 前置：确认种子依赖

E2E/黑盒依赖固定契约种子 `SHOP_A/SKU_A/prod-1`、`SHOP_B/SKU_B/prod-2`（见 `MallE2EIT.validateSeedContract`）。E4 外置后必须由 E2E 测试自身在 `@BeforeAll` 用 JDBC（`jdbc:postgresql://localhost:5433/tinystore`，user/pass `postgres`）写入这些行（幂等 `ON CONFLICT DO NOTHING`），否则 E2E 失败。

### E4: 外置 e2e 种子

**Files:**
- Delete: `tinystore-domain-inventory/src/main/resources/db/migration/inventory/V5__e2e_seed_stock.sql`
- Delete: `tinystore-domain-product/src/main/resources/db/migration/product/V7__e2e_seed_skus.sql`
- Create: `tests/api/src/test/resources/seed/e2e-seed.sql`
- Modify: `tests/api/src/test/java/com/github/spud/tinystore/tests/api/support/SeedData.java`（新，读 seed.sql，连 Postgres 执行）
- Modify: `tests/api/src/test/java/com/github/spud/tinystore/tests/api/MallE2EIT.java`：`@BeforeAll` 调 `SeedData.applyIfMissing()`，`validateSeedContract` 保持
- Modify: `tests/performance` 相关（如依赖种子，同样外置）；`tests/api/pom.xml` 增加 `postgresql` JDBC 依赖（test scope）

- [ ] 1. 把 V5/V7 的 INSERT 合入 `tests/api/src/test/resources/seed/e2e-seed.sql`（保留 `ON CONFLICT DO NOTHING`）。
- [ ] 2. 新建 `SeedData`：`@BeforeAll` 直连 `jdbc:postgresql://${POSTGRES_URL:-localhost:5433}/tinystore` 执行 seed.sql（幂等）。
- [ ] 3. `tests/api/pom.xml` 加 `org.postgresql:postgresql`（test）。
- [ ] 4. 删除 V5/V7 迁移文件。
- [ ] 5. 运行 `./mvnw -pl tinystore-domain-inventory verify -Pit` 确认无 seed 依赖崩溃；运行 `make e2e` 或手动 POSTGRES 校验种子存在、E2E 通过。

---

## E3: 生产配置与密钥管理

**Files:** `tinystore-domain-{gateway,auth,account,product,promotion,inventory,order,payment}/src/main/resources/application.yml` + 新增各模块 `application-prod.yml`

- [ ] 1. 各模块 `application.yml`：`password: postgres` → `password: ${DB_PASSWORD:postgres}`；DB/Redis/Kafka/Nacos 已多为 `${...}` 占位，保留。
- [ ] 2. gateway：`jwk-set-uri: ${JWKS_URI:https://auth.dev.tinystore.com/...}` → `${JWKS_URI:}`；`tinystore.security.resource-server.enabled` 默认保持 false，prod 可经 env 打开。
- [ ] 3. 各模块新增 `application-prod.yml`：DB/Redis/Kafka/Nacos 全部从 env 读，`DB_PASSWORD` 无默认（强制注入）；`SPRING_PROFILES_ACTIVE` 由部署环境设 `prod`。
- [ ] 4. 验证：`./mvnw -pl tinystore-domain-gateway,<modules> -am 验证` 编译通过；无 `.dev.tinystore.com` 出现在主配置默认值。配置=Exception 免 TDD，但需文档/编译验证。

---

## E2: 分布式链路追踪

**Files:** `tinystore-library-infrastructure/pom.xml`、根 `pom.xml`（若有版本管理）、各服务 `application.yml`

- [ ] 1. `tinystore-library-infrastructure/pom.xml` 加 `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`（版本随 Spring Boot BOM）。
- [ ] 2. 各服务 `application.yml` 加 `management.tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:0.1}`、`management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:}`（空=不导出，测试/本地安全）。
- [ ] 3. 新增单元测试：断言 `Tracer` bean 可注入且 `currentSpan` 存在（`TracingSmokeTest`）。Passive 追踪不改变业务行为（Falls through to TDD 冒烟验证）。

---

## E1: 端到端覆盖扩展

**Files:** `tests/api/src/test/java/com/github/spud/tinystore/tests/api/`

- [ ] 1. `e2e-seed.sql` 保证多 SKU/多店铺契约。
- [ ] 2. 新增 `E2EMultiShopFlowIT`：多店铺订单（SHOP_A+SHOP_B 各一行）→ 拆单 → 支付 → 各店铺发货 → 收货 → 断言两 ShopOrder 状态。
- [ ] 3. 新增 `E2EAfterSaleLifecycleIT`：下单→支付→收货→仅退款申请→审批→退款回调完成→断言售后状态与 PayStatus。
- [ ] 4. 新增 `E2ECancelTradeIT`：下单未付→取消→断言库存释放/交易关闭。
- [ ] 5. 统一在 `@BeforeAll` 调 `SeedData`，避免重复；补充 `@Tag("ep:...")` 保持端点覆盖契约。
- [ ] 6. 运行 `make e2e` 验证全部。

