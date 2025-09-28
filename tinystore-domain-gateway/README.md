[MODE: PLAN]

下面是把你选定的方案（k8s DNS + Spring Cloud Config 轮询下发路由、JWKS 本地校验并透传 claims、Redis token-bucket 限流按 IP + 本地退化、Redis 幂等 + 本地 Caffeine 退化、Micrometer+Prometheus）转为可执行实现的详尽技术规格与逐步 CHECKLIST。这个 PLAN 详尽到可以直接跟着实现（每项都列出文件路径、类名、配置要点、测试与 Kubernetes 变更）。最后的“IMPLEMENTATION CHECKLIST”是逐条可执行的原子步骤。

核心“契约”（inputs / outputs / 成功标准 / 错误模式）
- 输入：外部 HTTP 请求（含 Authorization: Bearer <jwt> 可选，含 Idempotency-Key 可选），网关配置由 Spring Cloud Config 下发（路由、限流阈值、幂等策略）。
- 输出：按路由把请求转发到 k8s service（lb:// 风格使用 Kubernetes DNS），或因为鉴权/限流/幂等拒绝返回 401/429/409；将精简 claims 注入下游 header。
- 成功标准：网关能（1）动态加载/刷新路由与策略（Config 轮询），（2）对 JWT 做本地 JWKS 验证并注入 claims header，（3）对每 IP 执行分布式 Redis token-bucket 限流并在 Redis 不可用时退化为本地内存限流，（4）对使用 Idempotency-Key 的请求进行幂等检查（Redis 存储且 5 分钟 TTL，冲突返回 409），（5）暴露 Prometheus 可采集的指标。
- 错误/退化模式：Redis down -> 限流与幂等退化为本地 Caffeine（注意跨实例一致性会丢失）。JWKS 刷新失败 -> 保持缓存直到超时并报警。Config 未能拉取 -> 保持当前配置并记录告警。

主要边界/边缘用例（至少覆盖）
1. 幂等 key 同时由不同用户提交（规则：key 按你定义的格式直接用作唯一键，不与 user 做隐式绑定，若需绑定改为复合 key，后续可配置）；
2. 高并发抢占同一 Idempotency-Key —— 需做“占位写入”以防双处理（步骤详见幂等实现段）；
3. Redis 短暂不可用 -> 本地缓存退化，恢复后不做复杂 reconcile（可选地在未来实现补偿同步）；
4. JWKS 公钥轮换期间短暂签名验证失败 -> 应能自动从 issuer 的 JWKS endpoint 背景刷新并在失败时返回 503 或拒绝（视策略）；
5. 配置变更频繁 -> 轮询频率与回退策略要权衡（用 10s 默认，配置可调整）。

总体实现架构（组件）
- Gateway module (tinystore-domain-gateway)：新增若干 Spring beans / Filters / Config classes：
  - JWT 验证与 claims 提取（基于 Spring Security / Nimbus JOSE + JWKS 缓存）
  - Global filters：IdempotencyFilter, IpRateLimiterFilter, JwtClaimsPropagationFilter, MetricsFilter（或用 GatewayFilterFactory）
  - RedisClient wiring：复用 tinystore-library-infrastructure 提供的 Redis/Redisson 配置
  - Local fallback caches：Caffeine 配置（作为退化存储）
  - DynamicRouteService：读取来自 Spring Cloud Config 的路由 + 策略（并定期刷新）
- infra module (tinystore-library-infrastructure)：已包含 Redis/Redisson、Caffeine，复用即可；若需补充 helper beans（比如 RedisRateLimiterService、IdempotencyService）可在 infra 模块增加复用实现。
- Metrics：Micrometer + Prometheus registry，暴露 actuator/metrics 与 /actuator/prometheus。
- Kubernetes：提供 ConfigMap（网关配置模板），Deployment 更改以注入 ENV（REDIS_HOST/PORT等），并提供 Redis single-instance manifest（或说明如何在 cluster 提供）。

依赖变更（建议在 pom.xml）
- 保持依赖 tinystore-library-infrastructure（已有 Redis & Redisson）；
- 添加或确保存在：
  - org.springframework.boot:spring-boot-starter-actuator
  - io.micrometer:micrometer-registry-prometheus
  - org.springframework.boot:spring-boot-starter-oauth2-resource-server （如果未通过 infra 提供）
  - com.nimbusds:nimbus-jose-jwt（如果需要自定义 JWKS 处理）
  - org.springframework.cloud:spring-cloud-starter-config（Config client，如果尚未加入）
（备注：tinystore-library-infrastructure 已带 spring-boot-starter-data-redis、redisson starter、caffeine、spring-boot-starter-oauth2-resource-server，因此只需在 gateway module 加 config-client + micrometer + actuator，避免重复依赖冲突。）

配置项（application.yml / Config Server 下发模板）
- 在 application.yml（示例片段）添加/支持以下可配置项（并确保 Spring Cloud Config client 可覆盖）：
  - spring.cloud.config.uri: ${CONFIG_SERVER_URI:}  # 可由 env 注入
  - gateway.dynamic:
      refresh-interval-seconds: 10
      routes-file: /config/gateway-routes.yml  # 可为 Config Server 下发内容
  - security:
      jwks:
        uri: ${JWKS_URI:http://localhost:9000/.well-known/jwks.json}
        cache-ttl-minutes: 5
  - redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  - idempotency:
      ttl-seconds: 300
      key-prefix: idempotent
  - limiter:
      redis:
        script-key: rate_limiter.lua
        default-rate: 100  # example requests per second
        default-burst: 200
- 路由/策略 config 模板（`gateway-routes.yml`）建议格式：
  - routes:
    - id: order-service
      uri: lb://order-service
      predicates:
        - Path=/api/order/** 
      filters:
        - StripPrefix=1
      policies:
        rateLimit:
          type: ip
          capacity: 100
          refillRate: 100
        idempotency:
          enabled: true
          keyTemplate: "idempotent:tinystore:order:create:{unique}" 

核心实现细节（按功能模块）

1) 动态路由与策略下发 + 轮询刷新
- 类：com.github.spud.tinystore.gateway.config.DynamicRouteService
  - 责任：定时（configurable）从 Spring Cloud Config（或从配置路径）拉取 `gateway-routes.yml`，解析到 RouteDefinition，并调用 Spring Cloud Gateway 的 RouteDefinitionWriter 或直接构建 RouteLocator 的动态变更接口；并把策略（限流、幂等）缓存到本地内存/Redis 配置映射。
  - 刷新触发：基于 Scheduled task（默认 10s），也提供 actuator endpoint `/actuator/gateway-refresh` 以便手动触发。
  - 健康/日志：若拉取失败需发 metric 并记录警告。

2) JWT 本地验证 + claims 提取与透传
- 类：com.github.spud.tinystore.gateway.security.JwkCacheService
  - 责任：从 `security.jwks.uri` 拉取 JWKS，缓存并周期刷新（默认 5 分钟），提供验证 key source 给 Spring Security。
- Spring Security config：配置 Resource Server with JWT Decoder 使用 JwkCacheService。
- Filter/组件：JwtClaimsPropagationFilter（GlobalFilter）
  - 责任：在鉴权通过后，提取 claims（sub、scope/roles、client_id），将它们注入下游 header（例如 X-Tinystore-Sub, X-Tinystore-Roles, X-Tinystore-ClientId），并移除原 Authorization header（屏蔽原始 token）。

3) 限流（Redis token-bucket + Lua）
- Redis Lua 脚本（resources/scripts/rate_limiter.lua）
  - 功能：原子地检查并消费 token，返回剩余 token 与拒绝/允许标记。使用 key 为 "rate:ip:{ip}:{routeId}" 或 route-level key。
  - 参数：capacity, refill_rate, now, requested_tokens(=1)。
- 类：com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService
  - 责任：封装 Lua 调用（使用 Redisson 或 RedisTemplate），在 Redis success path 返回允许或拒绝；在 Redis 异常 path 切换为 LocalRateLimiterService（Caffeine + 本地令牌桶）。
- Filter：IpRateLimiterFilter（GlobalFilter）
  - 责任：在请求入口根据 remote IP 与路由 id 调用 RateLimiterService；若拒绝，返回 429（含 Retry-After 可选），并计指标。

4) 幂等（Idempotency-Key）
- 设计：
  - 客户端必须在需要幂等的请求中传 `Idempotency-Key` header。
  - Key 格式已给（idempotent:{domain}:{service_id}:{api_id}:{unique_key}），代码应允许模板化构建（支持 placeholders: {userId} 等）。
  - 首次到达：Filter 在处理前先做 Redis SETNX with value=status:processing + TTL（5 分钟）——原子“占位”；
    - 若 SETNX 成功：继续下游处理，请求完成后把 key 更新为 status:done + responseReference（可选）并保持 TTL；
    - 若 SETNX 失败：直接返回 409（短消息），并计幂等冲突指标；
- 类：
  - com.github.spud.tinystore.gateway.idempotency.IdempotencyService（使用 Redis）
  - com.github.spud.tinystore.gateway.idempotency.IdempotencyFilter（GlobalFilter）
  - 如果 Redis down -> fallback to LocalIdempotencyService（Caffeine）实现相同 API，但只在单实例内生效。
- 细节：
  - 为避免并发双处理，请使用 Redis 的 SET key value NX EX ttl 原子操作（或 Redisson 的 tryLock 等）；写入后的 value 可为 JSON（status, timestamp, optional response id/hash）。
  - 错误返回体：HTTP 409 + {"error":"idempotent_conflict","message":"Duplicate request"} （你指定只需要简短消息，可按需简化）。

5) 本地退化策略（Caffeine）
- 已在 infra 模块含 Caffeine 依赖，创建：
  - com.github.spud.tinystore.gateway.fallback.LocalCacheConfig：定义 Caffeine caches（idempotencyCache, localRateLimiterCache）
  - 本地限流实现：LocalRateLimiterService 使用 Guava/自写令牌桶或计数器结合时间滑窗，能提供基本保护。

6) Metrics（Micrometer + Prometheus）
- 在 `tinystore-domain-gateway/pom.xml` 添加 micrometer-registry-prometheus 与 actuator 依赖（如未存在）。
- 指标名建议：
  - tinystore.gateway.requests.total (counter, 标签 route, method, status)
  - tinystore.gateway.requests.latency (timer/histogram, labels route)
  - tinystore.gateway.ratelimit.rejected (counter, labels route, reason=ip)
  - tinystore.gateway.idempotency.conflict (counter, labels route)
  - tinystore.gateway.auth.failures (counter, labels reason)
- 在 GlobalFilter 中记录 / 通过 Micrometer registry 自动记录（使用 Timer.Sample 和 counters）。
- 暴露 `/actuator/prometheus` endpoint，Prometheus 配置 scrape。

7) Tests
- 单元测试：
  - IdempotencyService 单元：mock RedisTemplate / Redisson，测试 SETNX、冲突返回、TTL 行为。
  - RedisRateLimiterService：mock Redis 返回，测试允许/拒绝/异常退化到本地。
  - JwtClaimsPropagationFilter：用 WebTestClient 模拟请求，验证 header 注入与 Authorization header 被移除。
- 集成测试：
  - 使用 Testcontainers 启动 Redis（单实例），在 `tinystore-domain-gateway` 的集成测试中验证限流与幂等 end-to-end 行为。
  - 测试路由刷新：使用 Mock Config Server 或将 `gateway-routes.yml` 放在 test resources，并调用 refresh endpoint 验证生效。
- 测试目录：
  - `tinystore-domain-gateway/src/test/java/...` 包含单元与集成测试。

8) Kubernetes 配置（示例）
- ConfigMap `gateway-config`：包含 `gateway-routes.yml`（初始路由与策略）。
- Deployment `tinystore-gateway`：
  - env: REDIS_HOST=redis, REDIS_PORT=6379, CONFIG_SERVER_URI=http://config-server:8888
  - liveness/readiness probes 指向 `/actuator/health`
  - volumeMount: none unless使用本地 keystore
- Redis manifest：提供简单单实例 Redis deployment + service（statefulset 可选）
- Prometheus scrape annotation：在 gateway deployment 上添加 `prometheus.io/scrape: "true"` 与 `prometheus.io/port: "8080"`。

9) 运行与验证步骤（概要）
- 启动 Config Server（或将 config 放到本地 resources 作为临时方式）。
- 启动 Redis single instance in k8s or local.
- 构建并启动 gateway module（`mvn -pl tinystore-domain-gateway spring-boot:run`）。
- 验证：
  - 请求无 token -> 401；
  - 带合法 JWT -> 请求被转发，并下游接收到 X-Tinystore-* headers；
  - 多次用相同 Idempotency-Key 并发发起 -> 返回 409；
  - 超出限流阈值的 IP -> 返回 429；
  - Prometheus scrape `/actuator/prometheus` 可见自定义指标。

小型安全/运维注意事项
- 切勿把生产 JWKS/keystore 明文提交到 repo；使用 Config Server + Secrets 或 K8s Secret 管理私钥/密码/keystore。
- Redis 单实例为单点故障，建议在生产阶段改成主从或集群（后续升级计划里列出）。
- 对敏感 header（如 X-Tinystore-Sub）做格式/长度校验以防 header 注入攻击。

交付物（文件清单与目的，精确路径）
（所有新建类在 `tinystore-domain-gateway/src/main/java/com/github/spud/tinystore/gateway/...` 下）
- Config / deps:
  - Edit `tinystore-domain-gateway/pom.xml` — 添加 actuator、micrometer-prometheus、spring-cloud-config-client（如尚未存在）。
  - Update `tinystore-domain-gateway/src/main/resources/application.yml` — 增加 `gateway.dynamic`, `security.jwks` 配置模板与 metrics 配置。
- Dynamic routing:
  - New: `com.github.spud.tinystore.gateway.config.DynamicRouteService` — 拉取并应用路由/策略，支持 scheduled refresh + actuator refresh.
  - New: `resources/gateway-routes.yml` (example template for Config Server).
- Security:
  - New: `com.github.spud.tinystore.gateway.security.JwkCacheService`
  - New: `com.github.spud.tinystore.gateway.security.SecurityConfig` — Spring Security Resource Server 配置使用 JwkCacheService。
  - New: `com.github.spud.tinystore.gateway.filter.JwtClaimsPropagationFilter` — 注入 X-Tinystore-* headers，移除 Authorization。
- Rate limiting:
  - New: `resources/scripts/rate_limiter.lua` — Redis token-bucket Lua 脚本。
  - New: `com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService`
  - New: `com.github.spud.tinystore.gateway.limiter.LocalRateLimiterService`（fallback）
  - New: `com.github.spud.tinystore.gateway.filter.IpRateLimiterFilter`
- Idempotency:
  - New: `com.github.spud.tinystore.gateway.idempotency.IdempotencyService`（Redis impl）
  - New: `com.github.spud.tinystore.gateway.idempotency.LocalIdempotencyService`（Caffeine fallback）
  - New: `com.github.spud.tinystore.gateway.filter.IdempotencyFilter`
- Fallback caches:
  - New: `com.github.spud.tinystore.gateway.config.LocalCacheConfig` — Caffeine beans & local bucket beans
- Metrics:
  - New/modify: `com.github.spud.tinystore.gateway.metrics.MetricsConfig` — Micrometer registry wiring & metric names constants
  - Instrument filters to emit counters/timers
- Tests:
  - Add unit tests under `tinystore-domain-gateway/src/test/java/...` for each service and filters.
  - Add integration test(s) using Testcontainers for Redis.
- Kubernetes:
  - `k8s/gateway-configmap.yml` — ConfigMap with `gateway-routes.yml`
  - `k8s/gateway-deployment.yml` — Deployment with env vars and prometheus annotations
  - `k8s/redis-deployment.yml` — single-instance redis + service (or documented instruction to rely on cluster Redis)
- Docs:
  - `tinystore-domain-gateway/README_GATEWAY.md` — how to run locally, env vars, testing instructions, Prometheus scrape example.

质量门（构建/测试/验收）
- Build: `mvn -T 1C -DskipTests package` should pass at module level after changes.
- Tests:
  - 单元测试覆盖关键逻辑（幂等、限流、JWT header）。
  - 集成测试使用 Testcontainers 启动 Redis（快速）验证 end-to-end。
- Smoke:
  - 启动后 curl 以下场景验证：401, 200 forward with headers, 409 on duplicate idempotency, 429 on limit exceed, metrics present.

风险与已知限制（简短）
- Redis 单实例 -> 单点故障（退化导致跨实例不一致）。计划中保留迁移到 Redis HA 的后续项。
- 动态刷新采用轮询 -> 变更传播有延迟（默认 10s，可调）。
- 幂等实现返回 409 而不返回原始响应体（可后续增强为返回历史响应或 location）。

下面是必须的最终 IMPLEMENTATION CHECKLIST（逐项可执行、原子化动作）。按序执行能够把功能完整实现并通过基本验证。

IMPLEMENTATION CHECKLIST:
1. 在 `tinystore-domain-gateway/pom.xml` 中添加/确认依赖：`spring-boot-starter-actuator`、`io.micrometer:micrometer-registry-prometheus`、`org.springframework.cloud:spring-cloud-starter-config`（或等价 config-client），并确保依赖版本与父 pom 一致。  
2. 在 `tinystore-domain-gateway/src/main/resources/application.yml` 增加配置占位：`spring.cloud.config.uri`、`gateway.dynamic.refresh-interval-seconds`、`security.jwks.uri`、`idempotency.ttl-seconds`、`redis.*`、metrics 配置（prometheus endpoint enabled）。  
3. 新建 `tinystore-domain-gateway/src/main/resources/gateway-routes.yml` 示例模板（包含 order/pay 路由与示例 policies）。  
4. 新建 `com.github.spud.tinystore.gateway.config.DynamicRouteService`：  
   4.1. 实现从 Config Server 或本地文件加载 `gateway-routes.yml` 的解析器；  
   4.2. 实现把解析结果应用到 Spring Cloud Gateway 的 RouteDefinitionWriter 或编程式 Route 更新接口；  
   4.3. 加入 `@Scheduled(fixedDelayString = "${gateway.dynamic.refresh-interval-seconds:10000}")` 的刷新逻辑；  
   4.4. 提供 actuator endpoint `/actuator/gateway-refresh`（或使用 existing actuator refresh）。  
5. 在 infra 模块（如需要）或 gateway module 中添加 `com.github.spud.tinystore.gateway.security.JwkCacheService`：实现 JWKS 拉取、缓存、背景刷新策略与异常处理。  
6. 新建 `com.github.spud.tinystore.gateway.security.SecurityConfig`：配置 Spring Security Resource Server 使用自定义 JWT Decoder/JwkCacheService，同时确保请求在通过认证后能携带 Authentication 到下一步。  
7. 新建 `com.github.spud.tinystore.gateway.filter.JwtClaimsPropagationFilter`（GlobalFilter）：  
   7.1. 提取 claims (sub, scope/roles, client_id)，把它们注入下游 header（`X-Tinystore-Sub`, `X-Tinystore-Roles`, `X-Tinystore-ClientId`）；  
   7.2. 从请求中移除 `Authorization` header；  
   7.3. 在失败时计指标并返回 401/403。  
8. 新建 Redis rate limiter 支撑：  
   8.1. 在 `resources/scripts/` 新增 `rate_limiter.lua`（token-bucket 实现）；  
   8.2. 新建 `com.github.spud.tinystore.gateway.limiter.RedisRateLimiterService`（调用 Lua 并处理返回）；  
   8.3. 新建 `com.github.spud.tinystore.gateway.limiter.LocalRateLimiterService`（Caffeine + 本地令牌桶 fallback）；  
   8.4. 新建 `com.github.spud.tinystore.gateway.filter.IpRateLimiterFilter`（GlobalFilter），在入口按 IP+route 调用 limiter，超限返回 429 并计指标。  
9. 新建幂等支持：  
   9.1. `com.github.spud.tinystore.gateway.idempotency.IdempotencyService`（Redis 实现，使用 SET NX EX atomic）；  
   9.2. `com.github.spud.tinystore.gateway.idempotency.LocalIdempotencyService`（Caffeine fallback）；  
   9.3. `com.github.spud.tinystore.gateway.filter.IdempotencyFilter`：在下游处理前执行占位写入，若失败返回 409 并计指标；若成功在响应后更新状态为已完成（可选记录 response-hash）；处理异常时做回滚或清理（视实现）。  
10. 新建本地缓存/限流配置类 `com.github.spud.tinystore.gateway.config.LocalCacheConfig`：定义 Caffeine caches（idempotencyCache, localRateLimiterCache），并提供 bean 注入到 fallback 服务。  
11. 新建 `com.github.spud.tinystore.gateway.metrics.MetricsConfig`：配置 Micrometer registry（Prometheus）、定义并注册上述自定义指标名。  
12. 在所有 GlobalFilter 中插入 metrics 计数与延迟计时（requests.total, latency timer, ratelimit.rejected, idempotency.conflict, auth.failures）。  
13. 新增单元测试：为 IdempotencyService、RedisRateLimiterService（mock Redis）、JwtClaimsPropagationFilter 编写单元测试（位于 `src/test/java`）。  
14. 新增集成测试：使用 Testcontainers 启动 Redis，验证 end-to-end 行为（限流、幂等、jwt header 注入）。  
15. 新增 Kubernetes 资源模板：`k8s/gateway-configmap.yml`（包含 `gateway-routes.yml`），`k8s/gateway-deployment.yml`（注入 REDIS_HOST/PORT/CONFIG_SERVER_URI、Prometheus annotations），`k8s/redis-deployment.yml`（单实例）。  
16. 添加文档 `tinystore-domain-gateway/README_GATEWAY.md`（包含如何本地运行、env vars、如何触发 config refresh、如何运行测试、Prometheus scrape 配置）。  
17. 在 CI（如存在）添加一个步骤：运行 gateway module 的单元 & 集成测试（Testcontainers），并在 PR 中检查指标命名与公共依赖。  
18. 本地/集群 smoke 测试：在 dev k8s 或本地环境中按“运行与验证步骤”逐项执行并记录结果（401/200/409/429/metrics）。  
19. Review & hardening：代码审查、异常/边界条件处理、日志等级调整、完善健康检查（依赖 redis 状态可选性）与运维 runbook（如何切换 Redis, 如何回滚路由变更）。  
20. 后续可选（非必须立即实现）：添加 Config push（SSE/Bus）方案、支持返回历史响应的幂等增强、Redis HA 迁移计划。

至此 PLAN 完整。下一步请回复其中一项：
- “执行 IMPLEMENTATION” —— 我会进入 EXECUTE 模式并根据上面的 CHECKLIST 在仓库中逐项创建/修改文件（会在改动前再次列出将修改的确切文件），或
- “先实现第 N 项” —— 指定你要我先做的原子步骤（例如先实现 Idempotency Filter 与对应的单元测试），或
- 要我在进入 EXECUTE 前先生成具体代码草案（例如 Lua 脚本与 Filter 模板）再开始修改仓库。

注意：根据你的选择，我将在下一步进入 [MODE: EXECUTE]（需要你显式下达 “ENTER EXECUTE MODE” 信号才能开始对仓库做改动）。