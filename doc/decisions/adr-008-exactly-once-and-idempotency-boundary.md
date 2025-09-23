# ADR-008: 恰好一次语义与幂等边界

## 状态
已接受

## 上下文
在分布式事件驱动系统中，我们需要明确"恰好一次"（Exactly-Once Semantics, EOS）的边界和幂等性策略。完全的端到端 EOS 在分布式系统中实现复杂且性能开销大，需要在一致性、性能和复杂度之间取得平衡。

## 决策

### 全局默认策略：至少一次 + 幂等
我们采用"至少一次传递 + 应用层幂等"作为默认策略，而非端到端的 EOS。

### EOS 不保证的边界点

#### 1. 外部系统集成点
- **第三方支付回调**：可能重复接收，依赖 `deliveryId` + `channel` 组合去重
- **物流回调**：可能重复接收，依赖业务幂等键去重
- **外部 API 调用**：网络重试可能导致重复，依赖幂等键

#### 2. 跨服务事件传递
- **事件发布**：生产者重试可能导致重复发布
- **事件消费**：消费者重启可能重复处理
- **跨主题消息**：不同主题间的消息不保证 EOS

#### 3. 读模型一致性
- **投影更新**：可能短暂不一致，最终一致性
- **缓存刷新**：缓存与数据库间可能不一致
- **搜索索引**：与主数据源可能存在延迟

### 幂等策略实现

#### 请求级幂等
```java
// API 请求幂等
@PostMapping("/orders")
public ResponseEntity<Order> createOrder(
    @RequestHeader("X-Idempotency-Key") String idempotencyKey,
    @RequestBody CreateOrderRequest request) {
    
    // 检查是否已处理
    if (idempotencyService.isProcessed(idempotencyKey)) {
        return idempotencyService.getResponse(idempotencyKey);
    }
    
    // 处理请求并记录结果
    Order order = orderService.createOrder(request);
    idempotencyService.recordResponse(idempotencyKey, order);
    
    return ResponseEntity.ok(order);
}
```

#### 事件级幂等
```java
// 事件消费幂等
@EventHandler
public void handle(EventEnvelope envelope) {
    String eventId = envelope.getEventId();
    
    // 基于 eventId 的幂等检查
    if (processedEventRepository.exists(eventId)) {
        log.debug("Event already processed: {}", eventId);
        return;
    }
    
    // 处理事件
    processEvent(envelope);
    
    // 记录处理状态
    processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
}
```

#### 回调级幂等
```java
// 支付回调幂等
@PostMapping("/payments/webhooks/{channel}")
public ResponseEntity<Void> handlePaymentWebhook(
    @PathVariable String channel,
    @RequestBody PaymentWebhookRequest request) {
    
    // 基于 deliveryId + channel 的幂等键
    String idempotencyKey = channel + ":" + request.getDeliveryId();
    
    if (webhookIdempotencyService.isProcessed(idempotencyKey)) {
        return ResponseEntity.ok().build();
    }
    
    paymentService.processWebhook(request);
    webhookIdempotencyService.markProcessed(idempotencyKey);
    
    return ResponseEntity.ok().build();
}
```

### 乱序与迟到事件处理

#### 时间窗口策略
- **容忍窗口**：5分钟内的事件可以乱序到达
- **迟到处理**：超出窗口的事件记录到专门表，触发告警
- **顺序恢复**：基于事件版本号进行顺序校验

#### 版本冲突处理
```java
public class EventVersionConflictHandler {
    public void handleVersionConflict(Event event, long expectedVersion) {
        if (event.getVersion() < expectedVersion) {
            // 迟到事件，记录但不处理
            lateEventRepository.save(event);
            alertService.notifyLateEvent(event);
        } else if (event.getVersion() == expectedVersion) {
            // 正常处理
            processEvent(event);
        } else {
            // 版本跳跃，可能丢失事件
            alertService.notifyVersionGap(event, expectedVersion);
        }
    }
}
```

### 补偿与错误恢复

#### 补偿策略清单
1. **重复处理**：通过幂等键识别并忽略
2. **丢失事件**：通过版本间隙检测和告警
3. **处理失败**：指数退避重试 + DLQ
4. **数据不一致**：定期对账和修复作业

#### 对账机制
```java
@Scheduled(fixedRate = 3600000) // 每小时执行
public void reconcileOrderPaymentStatus() {
    List<Order> orders = orderRepository.findOrdersInInconsistentState();
    
    for (Order order : orders) {
        PaymentStatus paymentStatus = paymentService.getStatus(order.getPaymentIntentId());
        
        if (isInconsistent(order.getStatus(), paymentStatus)) {
            // 记录不一致并触发修复
            inconsistencyService.record(order.getId(), paymentStatus);
            orderService.alignWithPaymentStatus(order.getId(), paymentStatus);
        }
    }
}
```

## 后果

### 优势
- **性能**：避免分布式事务的性能开销
- **可用性**：单点故障不会阻塞整个系统
- **简化**：降低系统复杂度，更容易理解和维护
- **可扩展性**：更容易水平扩展

### 权衡
- **最终一致性**：需要接受短暂的数据不一致
- **幂等复杂性**：需要在应用层实现幂等逻辑
- **对账成本**：需要额外的对账和修复机制
- **监控要求**：需要更完善的监控和告警

### 风险缓解
- **幂等测试**：为所有幂等逻辑编写专门测试
- **端到端监控**：监控业务流程的完整性
- **自动修复**：尽可能自动化不一致的检测和修复
- **手动介入**：为复杂场景提供手动修复工具

## 相关文档
- [事件信封](../messaging/event-envelope.md)
- [消费者拓扑](../messaging/consumer-topology.md)
- [DLQ 回放 Runbook](../operations/runbooks/dlq-replay.md)