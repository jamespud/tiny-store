# 订单支付成功回调垂直切片实现

## 概述

本项目实现了"支付成功回调→状态机→Outbox→Kafka事件→读侧同步"的完整垂直切片，采用 DDD（领域驱动设计）架构模式，展示了现代微服务架构中事件驱动、最终一致性、幂等性控制等核心模式的实际应用。

## 架构特点

### 1. DDD 分层架构
- **领域层 (Domain)**：聚合根、领域事件、状态机枚举
- **应用层 (Application)**：应用服务、业务流程编排
- **基础设施层 (Infrastructure)**：持久化、消息发布、状态机实现
- **接口层 (Interfaces)**：REST API、事件监听器

### 2. 核心技术栈
- **Spring Boot 3.5.0**：微服务框架
- **Spring State Machine**：状态机管理
- **PostgreSQL + JPA**：数据持久化
- **Redis**：缓存和幂等性控制
- **Apache Kafka**：事件流处理
- **Flyway**：数据库版本管理

### 3. 关键设计模式
- **Outbox Pattern**：确保事件发布的最终一致性
- **CQRS**：命令查询职责分离
- **Event Sourcing**：事件溯源和审计
- **Saga Pattern**：分布式事务管理
- **Idempotency**：幂等性控制

## 核心流程

### 支付成功回调处理流程

```
外部支付平台 -> REST API -> 幂等性检查 -> 加载聚合 -> 业务处理 -> 状态机 -> 持久化 -> Outbox事件 -> 审计记录 -> 响应
                    ↓
              定时任务扫描 -> Kafka发布 -> 下游系统消费 -> 读侧数据同步
```

### 详细步骤

1. **接收回调**：`OrderPaymentCallbackController` 接收支付平台回调
2. **幂等性控制**：`OrderIdempotencyService` 基于 requestId 防重复处理
3. **业务处理**：`OrderAggregateEnhanced` 处理支付成功业务逻辑
4. **状态管理**：`OrderStateMachineService` 控制订单状态流转
5. **事件发布**：`OutboxEventService` 保存领域事件到 Outbox 表
6. **异步发布**：`OutboxEventPublisher` 定时将事件发布到 Kafka
7. **审计跟踪**：`OrderStatusAuditService` 记录状态变更审计信息

## 文件结构

```
src/main/java/com/github/spud/tinystore/order/
├── application/service/
│   ├── OrderPaymentCallbackAppService.java     # 支付回调应用服务
│   └── OrderPaymentVerticalSliceDemo.java      # 演示测试
├── domain/
│   ├── event/                                  # 领域事件
│   │   ├── DomainEvent.java                   # 事件基类
│   │   ├── OrderPaidEvent.java                # 支付成功事件
│   │   └── OrderStatusChangedEvent.java       # 状态变更事件
│   ├── model/
│   │   └── OrderAggregateEnhanced.java        # 增强版订单聚合根
│   └── statemachine/
│       └── OrderStateMachineService.java      # 状态机服务接口
├── infrastructure/
│   ├── audit/
│   │   └── OrderStatusAuditService.java       # 状态审计服务
│   ├── config/                                # 配置类
│   │   ├── AsyncSchedulingConfig.java
│   │   ├── KafkaProducerConfig.java
│   │   └── RedisConfig.java
│   ├── event/
│   │   ├── outbox/
│   │   │   └── OutboxEventService.java        # Outbox 事件服务
│   │   └── publisher/
│   │       └── OutboxEventPublisher.java      # 事件发布器
│   ├── idempotency/
│   │   └── OrderIdempotencyService.java       # 幂等性服务
│   ├── persistence/
│   │   ├── po/                                # 持久化对象
│   │   │   ├── OrderOutboxEventPO.java
│   │   │   ├── OrderStatusAuditPO.java
│   │   │   └── OrderIdempotencyPO.java
│   │   └── repository/                        # 数据访问接口
│   │       ├── OrderOutboxEventRepository.java
│   │       ├── OrderStatusAuditRepository.java
│   │       └── OrderIdempotencyRepository.java
│   └── statemachine/
│       ├── OrderStateMachineConfig.java       # 状态机配置
│       └── OrderStateMachineServiceImpl.java  # 状态机实现
├── interfaces/web/
│   └── OrderPaymentCallbackController.java    # 支付回调控制器
└── statemachine/enums/                        # 状态机枚举
    ├── OrderMainStatus.java                   # 主状态
    ├── OrderSubStatus.java                    # 子状态
    └── OrderEvent.java                        # 状态机事件

src/main/resources/db/migration/
├── V1__create_outbox_and_audit.sql           # Outbox 和审计表
└── V2__create_order_idempotency.sql          # 幂等性表
```

## 数据库设计

### 核心表结构

1. **order_outbox_event** - Outbox 事件表
   - 存储待发布的领域事件
   - 支持重试和失败处理
   - 预留 CDC 变更捕获字段

2. **order_status_audit** - 状态审计表
   - 记录所有状态变更
   - 支持操作者追踪
   - 提供完整审计链路

3. **order_idempotency** - 幂等性控制表
   - 基于 requestId 防重复处理
   - 支持 24 小时 TTL
   - 状态跟踪和响应缓存

## 关键特性

### 1. 幂等性保证
- 基于 `requestId` 的幂等键机制
- 支持处理中、已完成、失败等状态
- 24 小时自动过期清理

### 2. 最终一致性
- Outbox Pattern 确保事件必达
- 定时重试失败事件
- 分布式事务通过事件协调

### 3. 状态机管理
- Spring State Machine 声明式配置
- 支持状态持久化和恢复
- 丰富的状态转换监听和日志

### 4. 审计追踪
- 完整的状态变更历史
- 操作者身份追踪
- 分布式追踪 ID 关联

### 5. 事件驱动架构
- 基于 Kafka 的异步消息传递
- 支持多主题分发
- 失败重试和监控告警

## API 接口

### 支付成功回调
```http
POST /api/orders/payment/callback/success
Content-Type: application/json

{
  "orderNo": "ORDER_20240101_001",
  "paymentTransactionId": "PAY_TXN_789123456",
  "paymentMethod": "ALIPAY",
  "amount": "99.99",
  "signature": "签名字符串"
}
```

### 查询回调结果
```http
GET /api/orders/payment/callback/result/{requestId}
```

## 配置说明

### Kafka 配置
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      retries: 3
      batch-size: 16384
      linger-ms: 1
      buffer-memory: 33554432
```

### Redis 配置
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tinystore_order
    username: postgres
    password: password
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
```

## 监控指标

### 业务指标
- 支付成功率
- 状态转换耗时
- 事件发布成功率
- 幂等性拦截率

### 技术指标
- Outbox 事件积压量
- Kafka 发布延迟
- 数据库连接池状态
- Redis 缓存命中率

## 运行演示

1. **启动基础服务**
   ```bash
   # 启动 PostgreSQL
   docker run -d --name postgres -p 5432:5432 -e POSTGRES_DB=tinystore_order -e POSTGRES_PASSWORD=password postgres:13

   # 启动 Redis
   docker run -d --name redis -p 6379:6379 redis:7-alpine

   # 启动 Kafka (需要先启动 Zookeeper)
   docker run -d --name zookeeper -p 2181:2181 confluentinc/cp-zookeeper:7.4.0
   docker run -d --name kafka -p 9092:9092 -e KAFKA_ZOOKEEPER_CONNECT=localhost:2181 confluentinc/cp-kafka:7.4.0
   ```

2. **运行演示测试**
   ```bash
   mvn test -Dtest=OrderPaymentVerticalSliceDemo
   ```

3. **发送回调请求**
   ```bash
   curl -X POST http://localhost:8080/api/orders/payment/callback/success \
     -H "Content-Type: application/json" \
     -d '{
       "orderNo": "ORDER_20240101_001",
       "paymentTransactionId": "PAY_TXN_789123456",
       "paymentMethod": "ALIPAY",
       "amount": "99.99",
       "signature": "test_signature"
     }'
   ```

## 扩展方向

1. **性能优化**
   - 批量事件处理
   - 分区策略优化
   - 缓存预热机制

2. **可靠性增强**
   - 死信队列处理
   - 熔断器模式
   - 限流和降级

3. **监控告警**
   - Prometheus 指标
   - Grafana 大盘
   - 钉钉/微信告警

4. **测试覆盖**
   - 集成测试
   - 压力测试
   - 混沌工程

## 总结

这个垂直切片演示了如何在实际项目中实现现代微服务架构的核心模式，包括：

- **DDD 领域建模**：清晰的领域边界和业务语言
- **事件驱动架构**：松耦合的系统集成方式
- **最终一致性**：分布式系统的数据一致性保证
- **幂等性设计**：防止重复处理的关键机制
- **可观测性**：完整的审计和追踪能力

通过这个实现，可以看到 DDD 不仅仅是建模方法，更是一套完整的软件架构解决方案。