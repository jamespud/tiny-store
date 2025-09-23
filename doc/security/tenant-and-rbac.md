# 多租户隔离与角色访问控制

## 概述

本文档定义多租户架构下的数据隔离、角色权限矩阵和安全边界。

## 多租户隔离模型

### 租户类型
```
Platform (平台层)
├── Merchant (商户层)
│   ├── Store (店铺层) 
│   └── User (用户层)
└── Internal (内部层)
    ├── Admin (管理员)
    └── Support (客服)
```

### 数据隔离策略

| 数据类型 | 隔离级别 | 实现方式 | 示例 |
|---------|---------|---------|------|
| 订单事件 | Merchant | 分区键 + 过滤 | `tenant_id` in partition key |
| 支付记录 | Merchant | 加密 + Row Level Security | PII 字段加密 |
| 库存数据 | Store | Schema 前缀 | `store_{id}_inventory` |
| 用户配置 | User | JWT Claim | `sub` + `tenant_id` |
| 平台配置 | Platform | 独立数据库 | `platform_config_db` |

### 租户上下文传播

```yaml
# 请求头标准
Headers:
  X-Tenant-ID: "merchant_123"
  X-Store-ID: "store_456"  # 可选
  X-User-ID: "user_789"    # 可选
  Authorization: "Bearer {JWT}"

# JWT Claims 结构
Claims:
  sub: "user_789"
  tenant_id: "merchant_123"
  store_id: "store_456"
  roles: ["STORE_MANAGER", "ORDER_VIEWER"]
  permissions: ["order:read", "inventory:write"]
```

## 角色权限矩阵 (RBAC)

### 预定义角色

| 角色 | 范围 | 描述 | 典型用户 |
|-----|------|------|---------|
| `PLATFORM_ADMIN` | Platform | 全局管理权限 | 内部运维 |
| `MERCHANT_OWNER` | Merchant | 商户全权管理 | 商户老板 |
| `STORE_MANAGER` | Store | 店铺运营管理 | 店长 |
| `CASHIER` | Store | 收银员权限 | 店员 |
| `CUSTOMER` | User | 普通用户权限 | 消费者 |
| `SUPPORT_AGENT` | Platform | 客服查看权限 | 客服 |

### 权限表 (Resources + Actions)

#### 订单权限
```yaml
order:
  - order:create     # 创建订单
  - order:read       # 查看订单
  - order:cancel     # 取消订单
  - order:refund     # 退款操作
  - order:split      # 拆单操作
```

#### 支付权限
```yaml
payment:
  - payment:read     # 查看支付状态
  - payment:callback # 处理支付回调
  - payment:refund   # 发起退款
  - payment:audit    # 支付审计
```

#### 库存权限
```yaml
inventory:
  - inventory:read   # 查看库存
  - inventory:write  # 修改库存
  - inventory:adjust # 库存调整
  - inventory:reserve # 预留库存
```

### 角色权限映射

| 角色 | 订单权限 | 支付权限 | 库存权限 | 其他权限 |
|-----|---------|---------|---------|---------|
| `PLATFORM_ADMIN` | 全部 | 全部 | 全部 | `platform:*` |
| `MERCHANT_OWNER` | 全部* | `read,audit` | 全部* | `merchant:*` |
| `STORE_MANAGER` | `create,read,cancel` | `read` | `read,write,adjust` | `store:*` |
| `CASHIER` | `create,read` | `read` | `read` | - |
| `CUSTOMER` | `create,read,cancel`** | `read`** | `read`** | `profile:*` |
| `SUPPORT_AGENT` | `read` | - | - | `support:*` |

*仅限自己的租户范围  
**仅限自己的资源

## 安全检查实现

### 服务级别拦截器

```java
@Component
public class TenantSecurityInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // 1. 提取租户上下文
        String tenantId = extractTenantId(request);
        String userId = extractUserId(request);
        
        // 2. 验证访问权限
        SecurityContext context = SecurityContext.builder()
            .tenantId(tenantId)
            .userId(userId)
            .roles(extractRoles(request))
            .build();
            
        // 3. 设置线程上下文
        TenantContext.set(context);
        
        return true;
    }
}
```

### 数据库层过滤

```sql
-- Row Level Security 示例 (PostgreSQL)
CREATE POLICY tenant_isolation ON orders
    FOR ALL TO application_role
    USING (tenant_id = current_setting('app.current_tenant_id'));

-- 查询时自动过滤
SELECT * FROM orders WHERE order_id = ?;
-- 实际执行：
-- SELECT * FROM orders WHERE order_id = ? AND tenant_id = 'merchant_123';
```

### 事件过滤

```java
@EventHandler
public void handle(OrderCreated event, @Header("tenant_id") String tenantId) {
    // 验证事件租户与处理器租户匹配
    if (!TenantContext.getTenantId().equals(tenantId)) {
        throw new TenantIsolationViolationException();
    }
    
    // 处理事件...
}
```

## 跨租户场景

### 平台级聚合查询
```yaml
# 场景：平台统计所有商户的销售数据
Authorization:
  Required Role: PLATFORM_ADMIN
  Data Access: 跨租户聚合视图
  Implementation: 
    - 使用专门的聚合服务
    - 数据去标识化处理
    - 审计日志记录
```

### 商户间数据共享
```yaml
# 场景：商户 A 的订单使用商户 B 的库存
Authorization:
  Required: 明确的数据共享协议
  Implementation:
    - 共享资源标记 (shared_resource_id)
    - 跨租户引用表
    - 共享权限审批流程
```

## 合规性考虑

### GDPR 数据主权
- 用户数据必须按地域隔离
- 跨境数据传输需明确授权
- 数据删除权必须按租户实现

### PCI DSS 隔离要求
- 支付数据单独加密密钥
- 不同租户的支付数据物理隔离
- 支付处理日志按租户审计

### SOX 合规 (如适用)
- 财务数据访问日志
- 角色变更审批流程
- 关键操作双人授权

## 实施检查清单

### Phase 1: 基础隔离
- [ ] JWT 中包含完整租户信息
- [ ] 所有 API 强制租户检查
- [ ] 数据库表包含 `tenant_id` 字段
- [ ] 事件信封包含租户上下文

### Phase 2: 权限控制
- [ ] 角色定义完整且最小权限
- [ ] 权限检查在业务逻辑前执行
- [ ] 跨服务调用传播权限上下文
- [ ] 管理界面支持角色分配

### Phase 3: 审计合规
- [ ] 所有权限检查被审计
- [ ] 敏感操作双重验证
- [ ] 数据访问模式监控
- [ ] 异常访问告警机制

## 参考

- [Gateway Authentication](../gateway/README.md) - 网关层认证实现
- [PII Data Protection](./pii.md) - 个人信息保护
- [Event Envelope](../messaging/event-envelope.md) - 事件中的安全头
- [ADR-004: Idempotency Keys](../decisions/adr-004-idempotency-keys-and-ttl.md) - 幂等性与安全