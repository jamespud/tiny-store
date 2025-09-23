# Schema Registry 与事件协议管理

本文档定义事件序列化协议、Schema 版本化策略、兼容性管理与演进流程，确保事件系统的稳定性和可扩展性。

## 序列化协议选择

### 推荐协议：JSON + Schema Version
- **格式**：JSON（人类可读，调试友好）
- **Schema 定义**：JSON Schema 或自定义规范
- **版本管理**：在事件信封中携带 `schemaVersion` 字段
- **兼容性**：通过 Upcaster 机制处理版本升级

### 可选协议：Apache Avro（高性能场景）
- **格式**：Avro 二进制序列化
- **Schema Registry**：Confluent Schema Registry 或兼容实现
- **兼容性**：原生支持 Schema 演进规则
- **适用场景**：高吞吐量、存储敏感的场景

## Schema 版本化策略

### 版本号规范
- **格式**：语义化版本 `major.minor.patch`
- **递增规则**：
  - `major`：破坏性变更（删除字段、更改字段类型）
  - `minor`：向后兼容的新增（新增可选字段）
  - `patch`：文档或注释更新

### Schema 定义示例
```json
{
  "eventType": "OrderCreated",
  "schemaVersion": "1.2.0",
  "schema": {
    "type": "object",
    "properties": {
      "orderId": {"type": "string"},
      "buyerId": {"type": "string"},
      "totalAmount": {"type": "number"},
      "currency": {"type": "string", "default": "CNY"},
      "items": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "skuId": {"type": "string"},
            "quantity": {"type": "integer"},
            "price": {"type": "number"}
          },
          "required": ["skuId", "quantity", "price"]
        }
      },
      "metadata": {
        "type": "object",
        "description": "新增于 v1.2.0，可选字段"
      }
    },
    "required": ["orderId", "buyerId", "totalAmount", "items"]
  }
}
```

## 兼容性管理

### 兼容性模式

#### Forward Compatibility（向前兼容）
- **定义**：新版本消费者可以处理旧版本数据
- **策略**：新增字段设为可选，提供默认值
- **示例**：v1.2 消费者可以处理 v1.0 事件

#### Backward Compatibility（向后兼容）
- **定义**：旧版本消费者可以处理新版本数据
- **策略**：不删除已有字段，不更改字段语义
- **示例**：v1.0 消费者可以处理 v1.2 事件（忽略新字段）

#### Full Compatibility（完全兼容）
- **定义**：新旧版本双向兼容
- **策略**：同时满足向前和向后兼容要求
- **推荐**：生产环境默认采用此模式

### 破坏性变更处理

#### 允许的安全变更
- 新增可选字段（带默认值）
- 扩展枚举值
- 增加文档和注释
- 放松验证规则（如增大字符串长度限制）

#### 禁止的破坏性变更
- 删除字段
- 重命名字段
- 更改字段类型
- 更改字段语义
- 收紧验证规则

#### 破坏性变更流程
1. **评估影响**：识别所有受影响的消费者
2. **制定迁移计划**：Upcaster 开发和部署计划
3. **并行期**：新旧 Schema 并行支持
4. **迁移窗口**：逐步迁移消费者到新版本
5. **清理期**：移除旧版本支持

## Upcaster 机制

### Upcaster 定义
Upcaster 是将旧版本事件数据转换为新版本格式的转换器，确保事件重放和历史数据处理的兼容性。

### 实现模式

#### 链式 Upcaster
```java
public interface EventUpcaster {
    String getTargetVersion();
    String getSourceVersion();
    Object upcast(Object oldEvent);
}

// 示例：OrderCreated v1.0 -> v1.1
public class OrderCreatedV1ToV1_1Upcaster implements EventUpcaster {
    @Override
    public String getSourceVersion() { return "1.0.0"; }
    
    @Override
    public String getTargetVersion() { return "1.1.0"; }
    
    @Override
    public Object upcast(Object oldEvent) {
        Map<String, Object> event = (Map<String, Object>) oldEvent;
        // 添加新字段 'currency'，默认值为 'CNY'
        event.putIfAbsent("currency", "CNY");
        return event;
    }
}
```

#### Upcaster 链管理
```java
public class UpcasterChain {
    private final Map<String, List<EventUpcaster>> upcasters;
    
    public Object upcastToLatest(String eventType, String fromVersion, Object event) {
        List<EventUpcaster> chain = upcasters.get(eventType);
        Object result = event;
        
        for (EventUpcaster upcaster : chain) {
            if (upcaster.getSourceVersion().equals(fromVersion)) {
                result = upcaster.upcast(result);
                fromVersion = upcaster.getTargetVersion();
            }
        }
        
        return result;
    }
}
```

### Upcaster 最佳实践
- **幂等性**：多次执行相同 Upcaster 结果一致
- **可测试性**：为每个 Upcaster 编写单元测试
- **最小化**：只转换必要的字段，保持原始数据不变
- **文档化**：记录每个版本变更的原因和转换逻辑

## Schema 注册与管理流程

### Schema 注册流程
1. **Schema 定义**：按规范定义新 Schema
2. **兼容性检查**：自动化工具验证兼容性
3. **审查流程**：团队 Code Review
4. **注册发布**：更新 Schema Registry
5. **版本通知**：通知相关团队更新

### Schema 存储结构
```
doc/messaging/schemas/
├── order/
│   ├── OrderCreated-v1.0.0.json
│   ├── OrderCreated-v1.1.0.json
│   └── OrderUpdated-v1.0.0.json
├── payment/
│   ├── PaymentSucceeded-v1.0.0.json
│   └── PaymentFailed-v1.0.0.json
└── inventory/
    └── InventoryReserved-v1.0.0.json
```

### 版本管理工具
- **Schema Registry**：集中式 Schema 存储和版本管理
- **兼容性检查**：CI/CD 中自动化兼容性验证
- **Schema 文档**：自动生成 Schema 文档和变更日志

## 性能与存储考虑

### JSON vs Avro 对比
| 维度 | JSON | Avro |
|------|------|------|
| 序列化速度 | 中等 | 快 |
| 反序列化速度 | 中等 | 快 |
| 存储大小 | 大 | 小 |
| 可读性 | 高 | 低 |
| Schema 演进 | 手动管理 | 原生支持 |
| 调试友好性 | 高 | 低 |

### 存储优化策略
- **压缩**：启用 Kafka 消息压缩（LZ4/Snappy）
- **批处理**：批量发送减少网络开销
- **分层存储**：冷数据迁移到对象存储

## 监控与运维

### 关键指标
- `schema_registry_requests_total`：Schema 查询请求数
- `schema_compatibility_check_failures`：兼容性检查失败数
- `upcaster_execution_time`：Upcaster 执行时间
- `schema_version_distribution`：各版本事件分布

### 告警规则
- Schema 兼容性检查失败
- Upcaster 执行时间过长
- 旧版本事件占比过高（需要推动升级）

## 灾难恢复

### Schema 备份策略
- **定期备份**：Schema Registry 数据定期备份
- **版本控制**：Schema 文件纳入 Git 版本控制
- **多环境同步**：开发/测试/生产环境 Schema 同步

### 恢复流程
1. **Schema 恢复**：从备份恢复 Schema Registry
2. **Upcaster 重建**：重新部署 Upcaster 逻辑
3. **兼容性验证**：验证事件处理兼容性
4. **渐进式恢复**：逐步恢复事件处理能力

## 开发者指南

### 事件发布者职责
- 定义清晰的 Schema 规范
- 遵循版本化策略
- 提供 Schema 变更通知
- 维护 Upcaster 实现

### 事件消费者职责
- 处理多版本事件格式
- 实现健壮的反序列化逻辑
- 及时升级到新 Schema 版本
- 报告不兼容问题

### 集成示例
```java
// 事件发布
EventEnvelope envelope = EventEnvelope.builder()
    .eventType("OrderCreated")
    .schemaVersion("1.2.0")
    .payload(orderCreatedEvent)
    .build();

producer.send(envelope);

// 事件消费
EventEnvelope envelope = consumer.receive();
Object event = upcasterChain.upcastToLatest(
    envelope.getEventType(),
    envelope.getSchemaVersion(),
    envelope.getPayload()
);
```