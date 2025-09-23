# 网关路由与鉴权透传

本文档定义 API 网关的路由规则、鉴权策略和请求上下文透传机制，确保统一的安全边界和服务间调用的信任传递。

## 路由规则

### 路径匹配规则
```yaml
# 示例网关配置
routes:
  - id: user-api
    uri: http://order-service:8080
    predicates:
      - Path=/api/v1/user/**
    filters:
      - AddRequestHeader=X-Gateway-Route,user-api
      - AuthFilter=USER
      
  - id: merchant-api
    uri: http://order-service:8080
    predicates:
      - Path=/api/v1/merchant/**
    filters:
      - AddRequestHeader=X-Gateway-Route,merchant-api
      - AuthFilter=MERCHANT
      
  - id: admin-api
    uri: http://order-service:8080
    predicates:
      - Path=/api/v1/admin/**
    filters:
      - AddRequestHeader=X-Gateway-Route,admin-api
      - AuthFilter=ADMIN
      
  - id: internal-api
    uri: http://order-service:8080
    predicates:
      - Path=/api/v1/internal/**
    filters:
      - AddRequestHeader=X-Gateway-Route,internal-api
      - AuthFilter=INTERNAL
```

### 服务发现与负载均衡
- **注册中心**：Kubernetes Service Discovery 或 Consul
- **负载均衡**：Round Robin + 健康检查
- **熔断器**：Circuit Breaker 模式，故障快速响应
- **重试策略**：指数退避，最大3次重试

## 鉴权策略

### 认证方式

#### 用户认证 (USER)
- **JWT Token**：Bearer Token in Authorization Header
- **Token 内容**：
  ```json
  {
    "sub": "user-12345",
    "tenant": "default",
    "roles": ["USER"],
    "iat": 1632150000,
    "exp": 1632153600
  }
  ```
- **验证方式**：JWT 签名验证 + Token 有效期检查

#### 商家认证 (MERCHANT)
- **JWT Token**：Bearer Token in Authorization Header
- **Token 内容**：
  ```json
  {
    "sub": "merchant-67890",
    "tenant": "merchant-portal",
    "roles": ["MERCHANT"],
    "shopId": "shop-001",
    "iat": 1632150000,
    "exp": 1632153600
  }
  ```
- **附加验证**：商家状态检查（激活/冻结/禁用）

#### 管理员认证 (ADMIN)
- **JWT Token**：Bearer Token in Authorization Header
- **Token 内容**：
  ```json
  {
    "sub": "admin-11111",
    "tenant": "admin-console",
    "roles": ["ADMIN", "ORDER_MANAGER"],
    "permissions": ["ORDER_VIEW", "ORDER_EDIT", "USER_MANAGE"],
    "iat": 1632150000,
    "exp": 1632150600
  }
  ```
- **权限验证**：细粒度权限检查

#### 内部调用认证 (INTERNAL)
- **API Key**：X-API-Key Header
- **mTLS**：双向 TLS 证书验证（生产环境推荐）
- **IP 白名单**：限制调用来源 IP
- **调用标识**：X-Calling-Service Header

### 鉴权失败处理
```json
// 401 Unauthorized
{
  "error": "unauthorized",
  "message": "Invalid or expired token",
  "timestamp": "2025-09-23T10:30:00Z",
  "path": "/api/v1/user/orders"
}

// 403 Forbidden
{
  "error": "forbidden", 
  "message": "Insufficient permissions",
  "required": ["ORDER_VIEW"],
  "current": ["USER"],
  "timestamp": "2025-09-23T10:30:00Z",
  "path": "/api/v1/admin/orders"
}
```

## 请求上下文透传

### 标准 Header 传递
网关自动添加以下 Headers 到下游服务：

```http
# 用户身份信息
X-User-Id: user-12345
X-User-Type: USER|MERCHANT|ADMIN|INTERNAL
X-Tenant-Id: default

# 商家特有信息（仅 MERCHANT 角色）
X-Shop-Id: shop-001

# 管理员权限信息（仅 ADMIN 角色）
X-Admin-Permissions: ORDER_VIEW,ORDER_EDIT,USER_MANAGE

# 追踪信息
X-Trace-Id: 1234567890abcdef
X-Correlation-Id: req-uuid-12345
X-Request-Id: gateway-req-67890

# 网关信息
X-Gateway-Route: user-api
X-Gateway-Version: 1.2.0
X-Client-IP: 192.168.1.100
X-User-Agent: TinyStore-Mobile/1.0.0
```

### 下游服务获取用户上下文
```java
@Component
public class RequestContextHolder {
    
    public static UserContext getCurrentUser() {
        HttpServletRequest request = getCurrentRequest();
        
        return UserContext.builder()
            .userId(request.getHeader("X-User-Id"))
            .userType(UserType.valueOf(request.getHeader("X-User-Type")))
            .tenantId(request.getHeader("X-Tenant-Id"))
            .shopId(request.getHeader("X-Shop-Id")) // 可选
            .permissions(parsePermissions(request.getHeader("X-Admin-Permissions"))) // 可选
            .traceId(request.getHeader("X-Trace-Id"))
            .correlationId(request.getHeader("X-Correlation-Id"))
            .build();
    }
}
```

## 限流与熔断

### 限流策略

#### 用户级限流
- **速率限制**：100 requests/minute per user
- **突发限制**：20 requests/10 seconds per user
- **实现方式**：Redis + Token Bucket 算法

#### 接口级限流
- **热点接口**：特殊限流规则
  - 创建订单：10 requests/minute per user
  - 支付接口：5 requests/minute per user
- **普通接口**：通用限流规则
  - 查询接口：200 requests/minute per user

#### 租户级限流
- **租户配额**：按租户分配 QPS 配额
- **公平调度**：防止单个租户占用过多资源
- **动态调整**：根据业务需求动态调整限流参数

### 熔断策略
```yaml
# 熔断器配置
circuit-breaker:
  failure-rate-threshold: 50%  # 失败率阈值
  wait-duration-in-open-state: 30s  # 熔断等待时间
  sliding-window-size: 100  # 滑动窗口大小
  minimum-number-of-calls: 10  # 最小调用次数
  permitted-calls-in-half-open: 5  # 半开状态允许调用数
```

## 内部服务调用信任

### 信任边界
```
Internet -> Gateway -> Internal Services (Trusted Zone)
```

### 内部调用认证
#### API Key 方式
```http
POST /api/v1/internal/orders/payment-callback
X-API-Key: internal-service-key-12345
X-Calling-Service: payment-service
Content-Type: application/json

{
  "orderId": "order-123",
  "paymentStatus": "SUCCEEDED"
}
```

#### mTLS 方式（推荐生产环境）
- **客户端证书**：每个服务有唯一的客户端证书
- **服务端验证**：验证客户端证书有效性和服务身份
- **证书轮换**：定期自动轮换证书

### 调用链追踪
```java
@RestTemplate
public class InternalServiceClient {
    
    public void callOrderService(String orderId) {
        HttpHeaders headers = new HttpHeaders();
        
        // 传递当前用户上下文
        UserContext context = RequestContextHolder.getCurrentUser();
        headers.set("X-Original-User-Id", context.getUserId());
        headers.set("X-Original-User-Type", context.getUserType().name());
        headers.set("X-Tenant-Id", context.getTenantId());
        
        // 调用链追踪
        headers.set("X-Trace-Id", context.getTraceId());
        headers.set("X-Correlation-Id", context.getCorrelationId());
        
        // 调用服务标识
        headers.set("X-Calling-Service", "payment-service");
        headers.set("X-API-Key", internalApiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        restTemplate.exchange("/api/v1/internal/orders/" + orderId, 
                             HttpMethod.GET, entity, Order.class);
    }
}
```

## 安全加固

### HTTPS/TLS 配置
- **TLS 版本**：TLS 1.2+ （禁用 TLS 1.0/1.1）
- **加密套件**：推荐 ECDHE + AES-GCM
- **证书管理**：Let's Encrypt 或企业 CA 证书
- **HSTS**：启用 HTTP Strict Transport Security

### 安全 Headers
```http
# 网关自动添加安全 Headers
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
Referrer-Policy: strict-origin-when-cross-origin
```

### 请求验证
- **请求大小限制**：10MB max payload
- **URL 长度限制**：8KB max URL length
- **Header 数量限制**：100 headers max
- **特殊字符过滤**：防止注入攻击

## 监控与日志

### 访问日志格式
```json
{
  "timestamp": "2025-09-23T10:30:00.000Z",
  "method": "POST",
  "path": "/api/v1/user/orders",
  "status": 201,
  "duration": 150,
  "userId": "user-12345",
  "userType": "USER",
  "tenantId": "default",
  "traceId": "1234567890abcdef",
  "correlationId": "req-uuid-12345",
  "clientIp": "192.168.1.100",
  "userAgent": "TinyStore-Mobile/1.0.0",
  "responseSize": 1024
}
```

### 关键指标
- `gateway_requests_total`：总请求数（按路径、状态码分类）
- `gateway_request_duration_seconds`：请求处理时间
- `gateway_auth_failures_total`：认证失败次数
- `gateway_rate_limit_hits_total`：限流触发次数
- `gateway_circuit_breaker_state`：熔断器状态

### 告警规则
- 认证失败率 > 5%
- 请求延迟 P95 > 500ms
- 熔断器打开状态持续 > 1 分钟
- 限流触发频率异常

## 相关文档
- [租户与 RBAC](../security/tenant-and-rbac.md)
- [API 契约治理](../api/README.md)
- [可观测性](../operations/observability.md)