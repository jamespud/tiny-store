# 读模型一致性与 SLA

本文档定义各读模型的一致性保证、新鲜度预算、缓存策略和降级方案，确保查询性能与数据一致性的平衡。

## 一致性级别定义

### 最终一致性 (Eventual Consistency)
- **定义**：数据最终会达到一致状态，但不保证实时一致
- **适用场景**：列表查询、统计报表、搜索结果
- **延迟容忍**：秒级到分钟级

### 近实时一致性 (Near Real-time Consistency)
- **定义**：数据在短时间内达到一致，通常在秒级
- **适用场景**：订单状态、支付状态、库存查询
- **延迟容忍**：毫秒级到秒级

### 强一致性 (Strong Consistency)
- **定义**：读取时保证数据与写入时一致
- **适用场景**：关键业务操作、金额计算
- **延迟容忍**：无延迟容忍

## 读模型 SLA 定义

### 订单相关读模型

#### order_summary (订单列表)
- **一致性级别**：最终一致性
- **新鲜度预算**：
  - P95: 30秒内反映最新状态
  - P99: 2分钟内反映最新状态
- **查询性能**：
  - P95: 响应时间 < 100ms
  - P99: 响应时间 < 300ms
- **可用性**：99.9%
- **降级策略**：缓存兜底，显示"数据可能延迟"提示

#### order_timeline (订单时间轴)
- **一致性级别**：近实时一致性
- **新鲜度预算**：
  - P95: 5秒内反映最新状态
  - P99: 30秒内反映最新状态
- **查询性能**：
  - P95: 响应时间 < 50ms
  - P99: 响应时间 < 150ms
- **可用性**：99.95%
- **降级策略**：事件源查询兜底

### 支付相关读模型

#### payment_status (支付状态查询)
- **一致性级别**：近实时一致性
- **新鲜度预算**：
  - P95: 3秒内反映最新状态
  - P99: 10秒内反映最新状态
- **查询性能**：
  - P95: 响应时间 < 30ms
  - P99: 响应时间 < 100ms
- **可用性**：99.99%
- **降级策略**：直查支付事件源

### 库存相关读模型

#### inventory_availability (库存可用性)
- **一致性级别**：近实时一致性
- **新鲜度预算**：
  - P95: 1秒内反映最新状态
  - P99: 5秒内反映最新状态
- **查询性能**：
  - P95: 响应时间 < 20ms
  - P99: 响应时间 < 50ms
- **可用性**：99.99%
- **降级策略**：缓存 + 保守估算

## 刷新策略

### 流式实时刷新
- **适用模型**：order_timeline, payment_status, inventory_availability
- **实现方式**：Kafka Streams 或自定义消费者
- **优势**：延迟低，数据新鲜
- **劣势**：资源消耗高，复杂度高

### 批量定时刷新
- **适用模型**：order_summary, 统计报表
- **刷新间隔**：30秒到5分钟不等
- **优势**：资源效率高，简单可靠
- **劣势**：延迟相对较高

### 事件驱动刷新
- **触发条件**：特定业务事件发生时触发
- **适用场景**：关键状态变更（支付成功、订单完成）
- **实现方式**：事件监听器 + 异步刷新

## 缓存策略

### 多级缓存架构
```
用户请求 -> Redis (L1) -> 应用缓存 (L2) -> 数据库 (L3)
```

### 缓存配置

#### 订单列表缓存
- **Redis TTL**：30秒
- **缓存键**：`order:list:user:{userId}:page:{page}:size:{size}`
- **失效策略**：订单状态变更时主动失效
- **容量限制**：单用户最多缓存10页数据

#### 订单详情缓存
- **Redis TTL**：60秒
- **缓存键**：`order:detail:{orderId}`
- **失效策略**：订单事件触发时主动失效
- **版本控制**：使用订单版本号避免脏读

#### 支付状态缓存
- **Redis TTL**：10秒
- **缓存键**：`payment:status:{intentId}`
- **失效策略**：支付事件触发时立即失效
- **一致性要求**：支付成功/失败必须实时反映

#### 库存可用性缓存
- **Redis TTL**：5秒
- **缓存键**：`inventory:available:{skuId}`
- **失效策略**：库存变更时立即失效
- **保守策略**：缓存过期时返回保守的库存估算

### 缓存预热策略
- **用户登录时**：预加载用户最近订单
- **商品浏览时**：预加载商品库存信息
- **支付流程**：预加载支付状态和订单详情

## UI 降级与回退

### 降级提示策略

#### 数据延迟提示
```javascript
// 前端显示逻辑
if (dataAge > thresholds.staleness) {
    showWarning("数据可能延迟，最后更新时间：" + lastUpdated);
}
```

#### 加载状态管理
```javascript
// 优雅降级显示
if (readModelUnavailable) {
    showSkeleton("正在加载最新数据...");
    fallbackToEventSource();
}
```

### 回退路径

#### 订单查询回退链
1. **主路径**：order_summary 缓存
2. **回退1**：order_summary 数据库直查
3. **回退2**：order.events 事件源重建
4. **最终回退**：显示错误页面，提供重试按钮

#### 支付状态回退链
1. **主路径**：payment_status 缓存
2. **回退1**：payment_status 数据库直查
3. **回退2**：payment.events 事件源聚合
4. **回退3**：第三方支付网关查询
5. **最终回退**：显示"查询中"状态，异步更新

#### 库存查询回退链
1. **主路径**：inventory_availability 缓存
2. **回退1**：inventory_availability 数据库直查
3. **回退2**：保守库存估算（设为0或很小值）
4. **最终回退**：显示"暂时无库存"

## 监控与告警

### 关键指标

#### 新鲜度指标
- `read_model_staleness_seconds`：读模型数据新鲜度
- `projection_lag_seconds`：投影延迟时间
- `event_to_projection_delay`：事件到投影的端到端延迟

#### 性能指标
- `read_model_query_duration_ms`：查询响应时间
- `cache_hit_rate`：缓存命中率
- `fallback_usage_rate`：降级路径使用率

#### 可用性指标
- `read_model_availability`：读模型可用性
- `cache_availability`：缓存层可用性
- `error_rate`：查询错误率

### 告警规则

#### 新鲜度告警
```yaml
- alert: ReadModelStaleness
  expr: read_model_staleness_seconds > 300
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "读模型数据过期"
    description: "{{ $labels.model }} 数据超过5分钟未更新"
```

#### 性能告警
```yaml
- alert: ReadModelSlowQuery
  expr: histogram_quantile(0.95, read_model_query_duration_ms) > 500
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "读模型查询性能下降"
    description: "{{ $labels.model }} P95查询时间超过500ms"
```

#### 可用性告警
```yaml
- alert: ReadModelUnavailable
  expr: read_model_availability < 0.99
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "读模型不可用"
    description: "{{ $labels.model }} 可用性低于99%"
```

## 重建与恢复

### 投影重建策略
- **增量重建**：从最后检查点开始重建
- **全量重建**：从事件源头完全重建
- **并行重建**：新旧投影并行运行，切换验证

### 重建 SLA
- **order_summary**：4小时内完成全量重建
- **order_timeline**：1小时内完成全量重建
- **payment_status**：30分钟内完成全量重建
- **inventory_availability**：15分钟内完成全量重建

### 数据验证
```java
public class ProjectionValidator {
    public ValidationResult validate(String model, Instant from, Instant to) {
        // 对比事件源和投影数据
        List<Inconsistency> inconsistencies = 
            compareEventSourceWithProjection(model, from, to);
        
        return ValidationResult.builder()
            .model(model)
            .period(from, to)
            .inconsistencies(inconsistencies)
            .accuracy(calculateAccuracy(inconsistencies))
            .build();
    }
}
```

## 容量规划

### 存储容量
| 读模型 | 当前大小 | 月增长 | 保留期 | 预计峰值 |
|--------|----------|--------|--------|----------|
| order_summary | 50GB | 10GB | 永久 | 200GB/年 |
| order_timeline | 20GB | 5GB | 2年 | 30GB |
| payment_status | 10GB | 3GB | 1年 | 15GB |
| inventory_availability | 1GB | 100MB | 实时 | 2GB |

### 查询容量
| 读模型 | 当前QPS | 峰值QPS | 预计增长 |
|--------|---------|---------|----------|
| order_summary | 100 | 1000 | 50%/年 |
| order_timeline | 50 | 500 | 30%/年 |
| payment_status | 200 | 2000 | 100%/年 |
| inventory_availability | 500 | 5000 | 200%/年 |

## 相关文档
- [事件信封](../messaging/event-envelope.md)
- [投影重建测试](../testing/projection-rebuild-tests.md)
- [可观测性](../operations/observability.md)