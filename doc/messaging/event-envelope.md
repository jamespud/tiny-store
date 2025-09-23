# 事件信封（Event Envelope）

事件信封是事件的标准化包装格式，包含事件元数据和业务载荷，确保事件在分布式系统中的可追溯性、版本兼容性和运维可观测性。

## 必填字段规范

### 核心元数据
- **eventId**：事件唯一标识符（UUID v4）
- **eventType**：事件类型（如 `OrderCreated`、`PaymentSucceeded`）
- **aggregateType**：聚合类型（如 `Order`、`Payment`）
- **aggregateId**：聚合实例 ID
- **version**：聚合版本号（用于并发控制）
- **occurredAt**：事件发生时间（ISO 8601 UTC 格式）
- **producerService**：事件发布者服务名

### 技术元数据
- **schemaVersion**：事件 Schema 版本（语义化版本）
- **traceId**：分布式追踪 ID（OpenTelemetry 兼容）
- **spanId**：当前 Span ID（可选，用于细粒度追踪）

### 业务载荷
- **payload**：事件业务数据（JSON 对象）
- **headers**：扩展头信息（Map<String, String>）

## 标准信封格式

```json
{
  "eventId": "123e4567-e89b-12d3-a456-426614174000",
  "eventType": "OrderCreated", 
  "aggregateType": "Order",
  "aggregateId": "order-789",
  "version": 1,
  "occurredAt": "2025-09-23T10:30:00.000Z",
  "producerService": "order-service",
  "schemaVersion": "1.2.0",
  "traceId": "1234567890abcdef",
  "spanId": "abcdef1234567890",
  "headers": {
    "tenant": "default",
    "correlationId": "req-12345",
    "idempotencyKey": "order_create_user123_20250923"
  },
  "payload": {
    "orderId": "order-789",
    "buyerId": "user-123",
    "totalAmount": 99.99,
    "currency": "CNY",
    "items": [
      {
        "skuId": "sku-001",
        "quantity": 2,
        "price": 49.99
      }
    ]
  }
}
```

## Headers 扩展字段规范

### 多租户字段
- **tenant**：租户标识符，用于多租户隔离
  - 格式：字符串，默认 "default"
  - 用途：数据隔离、权限控制
  - 参考：[租户与 RBAC](../security/tenant-and-rbac.md)

### 追踪与关联字段
- **correlationId**：请求关联 ID，串联业务流程
- **causationId**：因果关系 ID，标识触发此事件的命令或事件
- **userId**：操作用户 ID（如适用）
- **sessionId**：用户会话 ID（如适用）

### 幂等与重试字段
- **idempotencyKey**：幂等键，确保操作幂等性
  - 格式：`{operation}_{userId}_{timestamp}` 或业务自定义
  - 生命周期：24-72 小时
- **retryCount**：重试次数（用于 DLQ 处理）
- **originalEventId**：原始事件 ID（重试或回放场景）

### 安全与合规字段
- **signature**：事件签名（HMAC-SHA256，可选）
  - 用途：防篡改、身份验证
  - 算法：HMAC-SHA256(secret, eventId + eventType + aggregateId + payload)
- **encryptedFields**：加密字段列表（PII 保护）
- **auditLevel**：审计级别（LOW/MEDIUM/HIGH）

## 传输约定

### 生产端配置
- **幂等生产者**：启用 Kafka 幂等生产者配置
- **事务支持**：支持 Outbox 模式的事务性发布
- **批量发送**：支持批量发送提高吞吐量
- **压缩算法**：推荐 LZ4 或 Snappy 压缩

### 消费端处理
- **幂等消费**：以 `eventId` 作为幂等键去重
- **顺序保证**：同一 `aggregateId` 的事件在同一分区保序
- **错误处理**：反序列化失败发送到 DLQ
- **重试机制**：指数退避重试，最大重试次数限制

### 分区策略
- **分区键**：默认使用 `aggregateId`
- **热点处理**：高频聚合 ID 使用一致性哈希分桶
- **跨分区顺序**：不保证跨分区事件顺序

## 序列化与兼容性

### 序列化格式
- **主推荐**：JSON + SchemaVersion（调试友好）
- **高性能可选**：Apache Avro（存储和网络优化）
- **编码**：UTF-8

### 版本兼容性
- **策略**：向后兼容优先，支持 Schema 演进
- **处理机制**：Upcaster 机制处理版本升级
- **详细规范**：[Schema Registry](./schema-registry.md)

### 大小限制
- **信封总大小**：建议 < 1MB，硬限制 < 4MB
- **Payload 大小**：建议 < 512KB
- **Headers 大小**：建议 < 4KB
- **超大事件**：使用引用模式，Payload 存储外部地址

## 生产实践

### 事件发布示例
```java
EventEnvelope envelope = EventEnvelope.builder()
    .eventId(UUID.randomUUID().toString())
    .eventType("OrderCreated")
    .aggregateType("Order")
    .aggregateId(order.getId())
    .version(order.getVersion())
    .occurredAt(Instant.now())
    .producerService("order-service")
    .schemaVersion("1.2.0")
    .traceId(MDC.get("traceId"))
    .header("tenant", "tenant-001")
    .header("correlationId", request.getCorrelationId())
    .header("idempotencyKey", request.getIdempotencyKey())
    .payload(orderCreatedEvent)
    .build();

eventPublisher.publish("order.events", envelope);
```

### 事件消费示例
```java
@EventHandler
public void handle(EventEnvelope envelope) {
    // 幂等性检查
    if (processedEvents.contains(envelope.getEventId())) {
        log.debug("Event already processed: {}", envelope.getEventId());
        return;
    }
    
    // 版本兼容性处理
    Object payload = upcasterChain.upcastToLatest(
        envelope.getEventType(),
        envelope.getSchemaVersion(),
        envelope.getPayload()
    );
    
    // 业务处理
    switch (envelope.getEventType()) {
        case "OrderCreated":
            handleOrderCreated((OrderCreatedEvent) payload);
            break;
        // 其他事件类型...
    }
    
    // 记录处理状态
    processedEvents.add(envelope.getEventId());
}
```

## 监控与可观测性

### 关键指标
- `event_envelope_size_bytes`：事件信封大小分布
- `event_serialization_time_ms`：序列化时间
- `event_deserialization_time_ms`：反序列化时间
- `duplicate_event_rate`：重复事件比率
- `schema_version_distribution`：Schema 版本分布

### 日志规范
```json
{
  "level": "INFO",
  "timestamp": "2025-09-23T10:30:00.000Z",
  "service": "order-service",
  "traceId": "1234567890abcdef",
  "message": "Event published",
  "eventId": "123e4567-e89b-12d3-a456-426614174000",
  "eventType": "OrderCreated",
  "aggregateId": "order-789",
  "tenant": "tenant-001"
}
```

## 安全考虑

### PII 数据保护
- **字段级加密**：敏感字段单独加密
- **脱敏策略**：日志中脱敏 PII 字段
- **留存控制**：PII 数据按合规要求定期清理
- **详细规范**：[PII 保护](../security/pii.md)

### 访问控制
- **生产权限**：限制事件发布者权限
- **消费权限**：基于租户和角色的消费控制
- **审计要求**：记录敏感事件的访问日志

## 故障处理

### 常见问题
- **序列化失败**：Schema 不兼容或数据格式错误
- **重复消费**：幂等性机制失效
- **消息丢失**：生产者配置或网络问题
- **顺序错乱**：分区策略不当

### 诊断工具
- **事件查询**：按 eventId、aggregateId 查询事件
- **链路追踪**：基于 traceId 的完整调用链
- **Schema 验证**：事件格式合法性检查
- **重放工具**：历史事件重新处理

## 参考文档
- [消费者拓扑](./consumer-topology.md)
- [事件主题规划](./event-topics.md)
- [Schema Registry](./schema-registry.md)
- [术语表](../reference/glossary.md)
