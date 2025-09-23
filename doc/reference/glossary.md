# 术语表（Glossary）

## 核心领域术语

### 实体与聚合
- **Order**：订单聚合根，包含 SubOrder 和 LineItem
- **SubOrder**：子订单，按店铺/仓库/履约策略拆分
- **LineItem**：订单行项目，包含 SKU 快照与数量
- **PaymentIntent**：支付意图，记录支付请求与状态
- **PaymentTransaction**：支付交易，与网关交互的具体记录
- **Refund**：退款记录
- **InventoryReservation**：库存预占记录

### 状态与流程
- **CoreFlowStatus**：订单核心流程状态（Created→AwaitingPayment→Paid→Allocated→Shipped→Delivered→Completed/Cancelled）
- **Saga**：长事务编排，如 Checkout Saga
- **Outbox**：事务性消息发布模式
- **Event Sourcing (ES)**：事件溯源
- **CQRS**：命令查询职责分离

### 角色域（Role Domains）
- **user**：普通用户/买家
- **merchant**：商家/卖家
- **admin**：平台管理员
- **internal**：系统内部调用

### 消息与事件
- **Event Envelope**：事件信封，包含元数据的事件包装
- **Topic**：Kafka 主题
- **Partition Key**：分区键，通常为聚合 ID
- **Idempotency Key**：幂等键，确保操作幂等性
- **Schema Version**：模式版本，用于事件演进
- **Upcaster**：版本升级器，处理旧版本事件

## 缩写与简称

- **ES**：Event Sourcing（事件溯源）
- **CQRS**：Command Query Responsibility Segregation（命令查询职责分离）
- **DLQ**：Dead Letter Queue（死信队列）
- **TTL**：Time To Live（生存时间）
- **SLA**：Service Level Agreement（服务级别协议）
- **SLO**：Service Level Objective（服务级别目标）
- **RPO**：Recovery Point Objective（恢复点目标）
- **RTO**：Recovery Time Objective（恢复时间目标）
- **PCI DSS**：Payment Card Industry Data Security Standard（支付卡行业数据安全标准）
- **PII**：Personally Identifiable Information（个人可识别信息）
- **KMS**：Key Management Service（密钥管理服务）

## 命名规范

### API 路径规范
- 格式：`/api/v{版本}/{角色域}/{资源}`
- 示例：`/api/v1/user/orders`，`/api/v1/merchant/orders/{id}/ship`
- 内部接口：`/api/v1/internal/{资源}`

### 事件命名
- 格式：`{Domain}{Action}` 或 `{Aggregate}{Action}`
- 示例：`OrderCreated`，`PaymentSucceeded`，`InventoryReserved`

### 主题命名
- 格式：`{domain}.{type}`
- 类型：`events`（事件）、`commands`（命令）
- 示例：`order.events`，`payment.commands`

### 聚合与实体命名
- 聚合根：大写开头，单数形式（`Order`，`Payment`）
- 值对象：大写开头（`Money`，`Address`）
- 枚举：大写开头，复数形式（`OrderStatus`，`PaymentMethod`）

## 一致性术语

### 时间相关
- **occurredAt**：事件发生时间（UTC）
- **createdAt**：记录创建时间
- **updatedAt**：记录更新时间
- **expiredAt**：过期时间

### 标识符
- **id**：唯一标识符
- **aggregateId**：聚合根 ID
- **correlationId**：关联 ID，用于追踪请求链路
- **traceId**：分布式追踪 ID

### 状态与版本
- **version**：版本号，用于并发控制
- **status**：状态
- **state**：状态（在状态机上下文中）
- **schemaVersion**：模式版本

## 业务特定术语

### 电商领域
- **SKU**：Stock Keeping Unit（库存单位）
- **SPU**：Standard Product Unit（标准产品单位）
- **Fulfillment**：履约，包含发货、配送等
- **After-sale**：售后服务
- **Seckill**：秒杀活动

### 支付相关
- **Channel**：支付渠道（如微信、支付宝）
- **Gateway**：支付网关
- **Webhook**：支付回调
- **Tokenization**：令牌化，敏感信息替换为令牌

### 技术相关
- **Hot Key**：热点键，高频访问的键
- **Circuit Breaker**：熔断器
- **Rate Limiting**：限流
- **Backpressure**：背压