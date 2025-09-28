# TinyStore Gateway

TinyStore Gateway 是 TinyStore 微服务体系的统一入口，基于 Spring Cloud Gateway 实现动态路由、JWT 鉴权、IP 级限流、请求幂等等核心能力，并通过 Micrometer 输出统一指标。此文档概述运行方式与关键特性。

## 核心能力

- **动态路由**：从 `Config Server` 下发 `gateway-routes.yml`，支持热刷新，可通过 `Actuator` 端点 `/actuator/gateway/routes/refresh` 手动触发更新。
- **JWT 鉴权**：采用 `spring-security-oauth2-resource-server` 校验访问令牌，支持 JWKS 远端拉取与本地缓存，自动传播关键 Claims 到下游服务。
- **IP 限流**：默认使用 Redis Token Bucket（Lua 脚本实现，确保原子性），支持本地 Caffeine 缓存降级。
- **请求幂等**：PUT/POST 类敏感接口可根据客户端提供的幂等键（`Idempotency-Key` 头）确保请求只被下游处理一次。
- **指标监控**：通过 Micrometer 输出请求总数、耗时、限流/幂等触发次数、认证失败次数等指标，可由 Prometheus 抓取。

## 运行方式

### 1. 本地快速启动

1. 启动必要依赖（Redis、Config Server、Auth Server）。测试可使用 `docker-compose` 或本地容器快捷部署。
2. 在项目根目录执行：
   ```bash
   mvn -pl tinystore-domain-gateway spring-boot:run
   ```
3. 默认监听 `8080`，健康检查可访问 `/actuator/health`。

### 2. 运行单元与集成测试

```bash
mvn -pl tinystore-domain-gateway test
```

> 集成测试会自动使用 Testcontainers 启动 Redis 容器，请确保已安装 Docker。

### 3. Kubernetes 部署

`k8s/` 目录包含示例部署清单：

- `gateway-configmap.yml`：动态路由与策略示例
- `gateway-deployment.yml`：网关 Deployment 配置，挂载 ConfigMap
- `gateway-service.yml`：暴露网关服务（LoadBalancer）
- `redis-deployment.yml`：Redis Deployment + Service + ConfigMap

按照项目实际情况替换镜像、环境变量与密钥。

## 关键配置

`application.yml` 读取的主要环境变量：

- `SPRING_CONFIG_IMPORT`：Config Server 地址
- `SPRING_DATA_REDIS_HOST/PORT/PASSWORD`：Redis 连接信息
- `TINYSTORE_GATEWAY_JWKS_URI`：JWKS 端点
- `TINYSTORE_GATEWAY_IDEMPOTENCY_TTL`：幂等记录 TTL

## 指标列表（部分）

| 指标名 | 说明 |
| --- | --- |
| `gateway_requests_total` | 请求总数（标签：`routeId`,`outcome` 等） |
| `gateway_request_latency_seconds` | 请求耗时直方图 |
| `gateway_rate_limited_total` | 限流触发次数 |
| `gateway_idempotency_total` | 幂等命中/重复次数 |
| `gateway_auth_failure_total` | 鉴权失败次数 |

通过 `application.yml` 中的 `management.endpoints.web.exposure.include` 已开放基础健康检查与自定义刷新端点，可结合 Kubernetes 探针使用。

## 调试技巧

- 使用 `Idempotency-Key` header 验证幂等行为。
- 通过 `X-Forwarded-For` 头模拟不同客户端 IP 测试限流策略。
- 调整 Config Server 中的 `gateway-routes.yml`，并访问 `/actuator/gateway/routes/refresh` 验证热更新效果。
- 通过 `/actuator/prometheus` 拉取指标，确认指标输出正常。

## 后续增强建议

- 集成 OpenTelemetry 实现分布式追踪
- 增加 API 日志脱敏与异常统一处理过滤器
- 支持基于用户或租户的多维度限流策略
