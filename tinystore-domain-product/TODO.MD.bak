[MODE: PLAN]

下面给出“商品服务”在当前仓库中的落地化技术方案与实施细则，不包含任何实现代码，覆盖目录清理、依赖与持久化、事件与缓存、接口层、测试与运维等全链路改造，最后附上可直接执行的原子化实施清单。

## 范围与目标

- 目标模块：tinystore-domain-product（主改动），必要时少量变更 tinystore-domain-gateway（路由配置）。
- 目标达成：
  - 目录与命名统一（仓储接口与事件命名）
  - 面向对象规则引擎 + 规则持久化（PostgreSQL/Flyway）
  - 多租户行级隔离（BaseRepository 统一条件）
  - Outbox + Kafka 事件发布（复用基础设施组件）
  - 缓存策略（事件失效 + TTL，Apollo 动态配置）
  - REST API（Product/Sku/Pricing），鉴权与限流对齐网关
  - 单元/集成/契约测试，OpenTelemetry 可观测性对齐

## 设计选型到落地映射

- 规格唯一：规范化 `specCombination` + 唯一索引（`product_id + spec_combination`）
- 多租户：BaseRepository 统一追加 `tenant_id` 条件（避免 Hibernate Filter 绑定）
- 定价：面向对象规则 + 规则冲突静态校验；规则持久化 JSONB；灰度基于标签字段
- 版本用于订单重算：记录参与计算的规则版本数组（去重压缩存储）
- Outbox：复用基础设施组件；商品模块仅落地表与实体映射
- 缓存：事件驱动失效 + TTL 兜底；Apollo 动态可调
- 查询：列表交给搜索服务；后台报表采用 PG 物化视图（后续独立模块或脚本）
- API：纯 REST；预留未来 gRPC 扩展点（内部高频）

## 模块改造详规（不含代码）

### 1) 目录与命名清理

- 目录统一
  - 删除或迁移 `tinystore-domain-product/src/main/java/.../domain/repo` 下接口到 `.../domain/repository`
  - 全仓库更新引用路径（应用层、基础设施层、测试）
- 事件命名统一
  - 在 `.../domain/event` 中保留 `XxxEvent` 后缀类，删除无后缀的并行类（如 `PriceChanged`, `ProductCreated`）
  - 更新 `ProductService` 及其他发布处引用为 `ProductCreatedEvent`, `PriceChangedEvent`
- 包路径一致
  - 测试包 `domain.model.Sku` 调整为 `domain.model.aggregate.Sku`

### 2) 依赖与构建

- pom.xml
  - 增加：Spring Data JPA、PostgreSQL 驱动、Flyway、Spring for Kafka、OpenTelemetry（如基础设施未统一引入）
  - 测试依赖：jqwik（属性化测试）、Spring Cloud Contract（CDC）
- 统一编码与 Java 版本保留为 17
- Maven 插件
  - Flyway 迁移插件
  - 质量：Spotless/Checkstyle（可选），SonarQube 扫描（含多租户规则守护）

### 3) 数据模型与持久化（JPA 于基础设施层）

- 基础设施持久化包新增（仅类与文件名规划）
  - `.../infrastructure/persistence/jpa/entity/ProductEntity.java`
  - `.../infrastructure/persistence/jpa/entity/SkuEntity.java`
  - `.../infrastructure/persistence/jpa/entity/PricingRuleEntity.java`
  - `.../infrastructure/persistence/jpa/mapper/ProductMapper.java`（Domain ↔ Entity 转换）
  - `.../infrastructure/persistence/jpa/mapper/SkuMapper.java`
  - `.../infrastructure/persistence/jpa/mapper/PricingRuleMapper.java`
  - `.../infrastructure/persistence/jpa/repository/JpaProductRepository.java`
  - `.../infrastructure/persistence/jpa/repository/JpaSkuRepository.java`
  - `.../infrastructure/persistence/jpa/repository/JpaPricingRuleRepository.java`
- 领域仓储接口（放置处）
  - `.../domain/repository/ProductRepository.java`
  - `.../domain/repository/SkuRepository.java`
  - `.../domain/repository/PricingRuleRepository.java`
- BaseRepository（本模块内）
  - `.../infrastructure/persistence/jpa/base/BaseRepository.java`（统一追加 `tenant_id` 条件的方法约定）
  - `.../infrastructure/persistence/jpa/config/TenantRepositoryConfig.java`（从上下文解析 `tenantId` 的统一注入逻辑）
- 规格唯一
  - `sku` 表新增 `spec_combination` 字段；唯一索引 `uk_product_spec (product_id, spec_combination)`

### 4) 数据库迁移（Flyway 脚本）

- `src/main/resources/db/migration/`
  - `V1__init_product_tables.sql`（product、sku、类目关联/标签关联基础表；含版本与租户字段）
  - `V2__pricing_rule.sql`（`pricing_rule` 表，JSONB 内容，复合唯一 `uk_tenant_rule_code (tenant_id, rule_code)`）
  - `V3__outbox.sql`（通用 outbox 表结构，与基础设施对齐）
  - 必要索引：`product(tenant_id, category_id, status)`，`sku(product_id, spec_combination)`，`pricing_rule(tenant_id, product_id, category_id, status, effective_time, expire_time)`

### 5) Outbox 与事件发布

- 依赖 tinystore-library-infrastructure 的 Outbox 与 Kafka Publisher
- 本模块新增：
  - `.../infrastructure/outbox/OutboxEntity.java`（若基础设施要求模块内定义映射）
  - `.../infrastructure/outbox/OutboxServiceBridge.java`（桥接领域事件→Outbox 写入→Kafka 投递）
- `DomainEventPublisher` 的装配
  - `ProductConfig` 中将 `DomainEventPublisher` 绑定到 Outbox publisher 实现（保留 Logging 作为 fallback 开关）

### 6) 缓存（Redis）

- 缓存键
  - `product:{tenantId}:{productId}`
  - `sku:{tenantId}:{skuId}`
  - `pricing:rules:{tenantId}:{productId}`
  - `sku:dynamic:{tenantId}:{skuId}:{channel}`
- TTL（Apollo 配置）
  - `tinystore.product.cache.ttl.product`
  - `tinystore.product.cache.ttl.sku`
  - `tinystore.product.cache.ttl.pricing`
  - `tinystore.product.cache.ttl.channel`
- 事件驱动失效
  - 接收 `Product*Event`、`Sku*Event`、`PricingRuleChangedEvent` 删除对应键
- 缓存空值策略与预热脚本（大促前执行）

### 7) 定价规则持久化与执行

- 规则仓储
  - `PricingRuleRepository`（领域接口）
  - `JpaPricingRuleRepository`（Spring Data JPA）
- 规则解释器注册表
  - `.../domain/rules/RuleRegistry.java`（规则类型 → 构造/解释器）
- 规则冲突静态校验
  - `.../domain/rules/RuleConflictChecker.java`（校验优先级冲突、时间窗口重叠、互斥组覆盖）
- 订单重算版本
  - `PricingService` 返回对象中携带 `skuVersion` 与 `ruleVersions`（数组）

### 8) 应用层装配

- `.../interfaces/config/ProductConfig.java`（或 `.../infrastructure/config/ProductConfig.java`）
  - 手动装配 `ProductService`、`PricingService`、仓储实现、`DomainEventPublisher`、缓存组件
  - `@RefreshScope` 用于 Apollo 配置动态生效

### 9) 接口层（REST）

- 包结构
  - `.../interfaces/rest/ProductController.java`
  - `.../interfaces/rest/SkuController.java`
  - `.../interfaces/rest/PricingController.java`
  - DTO：`.../interfaces/dto/*`（Create/Update/Query/CalculatePrice）
- 端点（示意命名，不含代码）
  - Product：创建、更新、上下架、详情、标签维护
  - Sku：创建、更新（含动态属性）、详情
  - Pricing：`POST /api/products/calculate-price`（输入上下文、返回最终价/调整项/版本/签名）
- 鉴权
  - 读接口：校验 `X-Tenant-Id`
  - 写接口：JWT + 角色（`MERCHANT_ADMIN`/`PLATFORM_ADMIN`），`@PreAuthorize` 表达式与租户隔离对齐
- 限流与幂等
  - 配合网关 `RateLimitFilter`、`IdempotencyFilter`

### 10) 网关路由（在 tinystore-domain-gateway）

- `k8s/gateway-configmap.yml` 或 `src/main/resources/gateway-routes.yml`
  - 新增 `product-service` 路由，断言 `Path=/api/products/**`，过滤器剥前缀与限流配置

### 11) 配置

- application.yml
  - DB、Kafka、Redis、Apollo、资源服务器 JWT（沿用现有）
  - 新增 `tinystore.product.cache.ttl.*` 默认值
  - Kafkatopic 命名：`tinystore.product.events`、`tinystore.sku.events`、`tinystore.pricing.events`、`tinystore.category.events`
- `application-*.yml`
  - 测试与本地的内置配置（例如内存/容器化 Postgres）

### 12) 测试与质量

- 单元测试
  - 规则引擎属性化测试（jqwik）：优先级/互斥/时间窗口/标签匹配组合
  - 聚合不变式测试：类目叶子约束、SKU 规格唯一、ARCHIVED 不可写
- 集成测试
  - 仓储 JPA + Testcontainers（PostgreSQL）
  - Flyway 脚本迁移验证
- 契约测试（CDC）
  - 事件契约 JSON Schema；生产者侧 Spring Cloud Contract
- 代码扫描
  - SonarQube：定制规则检查 JPQL/SQL 必含 `tenantId` 约束

### 13) 可观测性与审计

- OpenTelemetry：HTTP/Kafka trace 透传
- 审计日志
  - 表结构与 ES 双写流水；敏感字段掩码
  - 后台检索对齐运维规范

### 14) 运维与切换

- 开关
  - `feature.pricing-rule.enabled`
  - `feature.outbox.enabled`
- 回滚预案
  - Flyway 回滚脚本（谨慎；必要时使用变更对冲）
  - 事件发布降级到日志发布
- 大促前演练
  - 规则/索引预热、缓存 TTL 下调、Kafka 与 DB 压测阈值确认

---


## IMPLEMENTATION CHECKLIST（对齐补全版）

1. 在 tinystore-domain-product 中删除/迁移目录：
   1.1 将 src/main/java/.../domain/repo 下所有接口移动到 src/main/java/.../domain/repository  
   1.2 全项目（含测试）更新 import 路径引用到 domain/repository  
   1.3 删除空的 domain/repo 目录  
   1.4 约定 dev/test profile 仍可使用 InMemory 仓储（位于 infrastructure/repo），prod 使用 JPA 仓储（见第 11.3 的装配约定）

2. 统一事件命名：
   2.1 在 src/main/java/.../domain/event 中删除无后缀事件类（如 PriceChanged、ProductCreated）  
   2.2 将所有发布事件的调用点替换为 XxxEvent（如 ProductCreatedEvent、PriceChangedEvent）  
   2.3 更新测试与任何引用处的类名  
   2.4 在配置中为事件类型与 Topic 的映射预留键（见第 15.1）

3. 调整测试包路径：
   3.1 将测试中引用的 domain.model.Sku 更正为 domain.model.aggregate.Sku  
   3.2 修复可能的导入路径与断言依赖  
   3.3 对现有测试使用的内存仓储，显式标注为 dev/test profile

4. 更新 pom.xml 依赖（仅列出坐标名称，不粘贴）：
   4.1 添加 Spring Data JPA、PostgreSQL Driver、Flyway、Spring for Kafka、OpenTelemetry  
   4.2 添加测试依赖 jqwik、Spring Cloud Contract（生产者侧）  
   4.3 保持 Spring Boot Starter 与 Validation；确认 lombok 配置  
   4.4 插件与基础配置：Java 17、Surefire/Failsafe、Spotless/Checkstyle（如启用）、SonarScanner；Flyway 插件（可选）

5. 新增基础设施持久化层文件（空类与注释骨架即可）：
   5.1 infrastructure/persistence/jpa/entity/ProductEntity.java  
   5.2 infrastructure/persistence/jpa/entity/SkuEntity.java  
   5.3 infrastructure/persistence/jpa/entity/PricingRuleEntity.java  
   5.4 infrastructure/persistence/jpa/mapper/ProductMapper.java  
   5.5 infrastructure/persistence/jpa/mapper/SkuMapper.java  
   5.6 infrastructure/persistence/jpa/mapper/PricingRuleMapper.java  
   5.7 infrastructure/persistence/jpa/repository/JpaProductRepository.java  
   5.8 infrastructure/persistence/jpa/repository/JpaSkuRepository.java  
   5.9 infrastructure/persistence/jpa/repository/JpaPricingRuleRepository.java  
   5.10 实体字段要求：tenant_id、业务主键、乐观锁 version、审计字段（created_at、updated_at）、必要二级索引（与第 8 的迁移一致）

6. 定义领域仓储接口（若尚未齐全）：
   6.1 domain/repository/ProductRepository.java  
   6.2 domain/repository/SkuRepository.java  
   6.3 domain/repository/PricingRuleRepository.java（含按 tenant+product/category/tags、rule_code、时间窗口的查询方法定义）

7. 引入 BaseRepository 统一租户条件：
   7.1 infrastructure/persistence/jpa/base/BaseRepository.java 定义统一查询约束方法（例如包装 Specification/Query 统一追加 tenant_id）  
   7.2 infrastructure/persistence/jpa/config/TenantRepositoryConfig.java 提供 tenantId 注入机制（优先 X-Tenant-Id Header，回落 SecurityContext；使用 RequestScope/ThreadLocal 提供者）  
   7.3 让 Jpa*Repository 通过 BaseRepository 的统一入口执行查询/更新  
   7.4 引入 Sonar 自定义规则：静态检查 JPQL/SQL 必含 tenant 约束（在 CI 强制，见第 18）

8. 落地 Flyway 迁移脚本：
   8.1 新建 src/main/resources/db/migration/V1__init_product_tables.sql（product、sku、标签/类目关联；含审计、version、tenant_id）  
   8.2 新建 V2__pricing_rule.sql（JSONB、uk_tenant_rule_code(tenant_id, rule_code) 及查询索引）  
   8.3 新建 V3__outbox.sql（与基础设施 outbox 对齐：id, aggregate_id, event_type, payload, occurred_at, published 等）  
   8.4 在 sku 表新增 spec_combination 字段与 uk_product_spec (product_id, spec_combination)  
   8.5 在 application.yml 启用 Flyway 并配置数据源（仅占位键，不含敏感信息）

9. Outbox 集成：
   9.1 若基础设施要求：新增 infrastructure/outbox/OutboxEntity.java 与 OutboxServiceBridge.java（领域事件 → Outbox → Kafka）  
   9.2 在应用装配中将 DomainEventPublisher 绑定到 Outbox Publisher，实现事件→Outbox→Kafka 的链路  
   9.3 通过 Apollo/配置中心开关 feature.outbox.enabled 控制启用/禁用，禁用时回退至 LoggingEventPublisher

10. 缓存策略接入：
   10.1 定义缓存键命名规范（product/sku/pricing/channel）  
   10.2 在 application.yml 中新增 tinystore.product.cache.ttl.* 默认值（支持 @RefreshScope）  
   10.3 接入事件监听：收到 Product*Event、Sku*Event、PricingRuleChangedEvent 时删除对应缓存键（列出事件→Key 的映射关系）  
   10.4 约定空值缓存策略（防穿透）与 TTL；新增大促缓存预热入口（运维脚本或 Admin 接口）

11. 规则持久化与执行装配：
   11.1 完善 PricingRuleRepository 与 JpaPricingRuleRepository 的方法集合（按 tenant+product/category/tags、有效期过滤、优先级排序）  
   11.2 新增 RuleRegistry（规则类型与构造/解释器注册表，不绑定具体实现细节）  
   11.3 新增 RuleConflictChecker（静态校验优先级、互斥组、时间窗口重叠）  
   11.4 确认 PricingService 的结果模型包含 skuVersion 与 ruleVersions 数组；对外返回应包含这些版本用于订单重算（接口层一并体现）

12. 应用装配类：
   12.1 新增 interfaces/config/ProductConfig.java（或 infrastructure/config）  
   12.2 在其中以 @Bean 形式装配 ProductService、PricingService、仓储（dev/test 使用 InMemory，prod 使用 JPA）、DomainEventPublisher、缓存组件、RuleRegistry、RuleConflictChecker  
   12.3 对需动态刷新的 Bean 使用 @RefreshScope（如 TTL、feature 开关）

13. 接口层文件创建：
   13.1 interfaces/rest/ProductController.java（创建/更新/上下架/详情/标签）  
   13.2 interfaces/rest/SkuController.java（创建/更新/动态属性/详情）  
   13.3 interfaces/rest/PricingController.java（POST /api/products/calculate-price）  
   13.4 interfaces/dto/*：输入输出 DTO（含校验注解：非空、长度、范围、货币/数量格式等；定价结果含 adjustments、finalPrice、skuVersion、ruleVersions）  
   13.5 为写接口添加 @PreAuthorize 与幂等 Idempotency-Header 存在性校验与透传（去重由网关/跨服务统一组件处理）

14. 网关路由配置（在 tinystore-domain-gateway）：
   14.1 在 gateway-routes.yml（或 k8s ConfigMap）增加 product-service 路由：Path=/api/products/**，StripPrefix=1  
   14.2 为该路由开启限流与幂等策略模板（容量与 TTL 占位）；提交后验证路径转发与 401/403/429 等行为

15. 配置文件完善：
   15.1 application.yml 补充 DB/Kafka/Redis/Apollo/安全配置与 topic 名称（tinystore.product.events、tinystore.sku.events、tinystore.pricing.events、tinystore.category.events）  
   15.2 新增 application-local.yml 与 application-test.yml（本地与测试的连接信息与最小可运行配置；敏感信息用占位）  
   15.3 确认 OpenTelemetry 自动配置开启（HTTP/Kafka trace 透传），设置基本采样率与导出端点占位

16. 测试补齐：
   16.1 规则引擎属性化测试（jqwik）：优先级/互斥/时间窗口/人群标签组合  
   16.2 聚合不变式测试：叶子类目、SKU 唯一、ARCHIVED 不可写  
   16.3 仓储集成测试（Testcontainers + Flyway + PostgreSQL）：迁移、唯一约束、多租户隔离查询  
   16.4 缓存与事件失效测试（Testcontainers Redis 或 embedded-redis）：发布事件后验证 key 删除  
   16.5 事件契约测试（Spring Cloud Contract）：定义 ProductCreatedEvent、PriceChangedEvent 等 JSON Schema 并生成用例

17. 可观测性与审计：
   17.1 确认 OpenTelemetry traceId 在 HTTP 入/出与 Kafka 事件中透传到日志  
   17.2 审计日志落表与（可选）Kafka->ES 双写流程占位；定义敏感字段掩码策略  
   17.3 关键写操作埋点：用户、租户、动作、目标、结果、请求幂等键

18. 质量与 CI：
   18.1 启用 Spotless/Checkstyle（若未启用），提交前校验  
   18.2 SonarQube 规则集接入并在 CI 中强制（包含“JPQL/SQL 必含 tenant 约束”与测试覆盖率阈值）  
   18.3 在 CI 中增加：Flyway 迁移校验、单测（jqwik）、集测（Testcontainers PG/Redis）、契约测试、打包与制品归档

19. 演练与切换：
   19.1 演练：在测试环境完成全链路（DB 迁移、规则持久化、事件发布、缓存失效）并留存基线数据  
   19.2 上线窗口前冻结变更并执行灰度（打开 feature.pricing-rule.enabled 与 feature.outbox.enabled，小流量验证）  
   19.3 大促前执行缓存预热与 TTL 下调（Apollo 灰度配置），确认 Kafka 与 DB 阈值

20. 清理与文档：
   20.1 移除遗留类与未使用依赖  
   20.2 更新 README 与 ADR（架构决策记录），记录选型与扩展点  
   20.3 输出 API Swagger 文档与事件契约文档（随制品发布）
