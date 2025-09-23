# 事件主题与所有权管理

本文档定义事件主题的命名规范、分区策略和事件所有权，确保事件系统的清晰边界和责任分工。

## 主题命名规范

### 命名格式
格式：`{domain}.{type}`

- **domain**：领域名称（order、payment、inventory、user、product）
- **type**：消息类型（events、commands）

### 主题类型说明
- **events**：领域事件，记录已发生的业务事实
- **commands**：领域命令，表示待执行的操作请求

### 主题列表
| 主题名 | 类型 | 描述 | 分区数 | 副本数 |
|--------|------|------|--------|--------|
| order.events | Events | 订单领域事件 | 12 | 3 |
| order.commands | Commands | 订单领域命令 | 6 | 3 |
| payment.events | Events | 支付领域事件 | 8 | 3 |
| payment.commands | Commands | 支付领域命令 | 4 | 3 |
| inventory.events | Events | 库存领域事件 | 8 | 3 |
| inventory.commands | Commands | 库存领域命令 | 4 | 3 |
| user.events | Events | 用户领域事件 | 4 | 3 |
| product.events | Events | 商品领域事件 | 6 | 3 |

## 事件所有权表

### 订单领域 (Order Domain)

#### 发布的事件 (Published by Order Service)
| 事件类型 | 主题 | 分区键 | 序列化 | 兼容模式 | SLA | 描述 |
|----------|------|--------|--------|----------|-----|------|
| OrderCreated | order.events | orderId | JSON | Full | P95<100ms | 订单创建 |
| OrderSubmitted | order.events | orderId | JSON | Full | P95<100ms | 订单提交 |
| OrderCancelled | order.events | orderId | JSON | Full | P95<100ms | 订单取消 |
| OrderShipped | order.events | orderId | JSON | Full | P95<100ms | 订单发货 |
| OrderDelivered | order.events | orderId | JSON | Full | P95<100ms | 订单妥投 |
| OrderCompleted | order.events | orderId | JSON | Full | P95<100ms | 订单完成 |
| AfterSaleApplied | order.events | orderId | JSON | Full | P95<200ms | 售后申请 |
| AfterSaleApproved | order.events | orderId | JSON | Full | P95<200ms | 售后审批 |

#### 消费的事件 (Consumed from Other Domains)
| 事件类型 | 来源主题 | 发布者 | 处理用途 | 重试策略 |
|----------|----------|--------|----------|----------|
| InventoryReserved | inventory.events | inventory-service | 推进订单到已分配状态 | 指数退避，最大5次 |
| InventoryReservationFailed | inventory.events | inventory-service | 订单取消，释放资源 | 指数退避，最大3次 |
| InventoryCommitted | inventory.events | inventory-service | 确认库存分配 | 指数退避，最大5次 |
| InventoryReleased | inventory.events | inventory-service | 库存释放确认 | 指数退避，最大3次 |
| PaymentSucceeded | payment.events | payment-service | 推进订单到已支付状态 | 指数退避，最大5次 |
| PaymentFailed | payment.events | payment-service | 订单支付失败处理 | 指数退避，最大3次 |
| RefundSucceeded | payment.events | payment-service | 退款成功确认 | 指数退避，最大3次 |

#### 发布的命令 (Published Commands)
| 命令类型 | 目标主题 | 目标服务 | 分区键 | 超时 | 描述 |
|----------|----------|----------|--------|------|------|
| ReserveInventoryCommand | inventory.commands | inventory-service | skuId | 30s | 预占库存 |
| CommitInventoryCommand | inventory.commands | inventory-service | skuId | 30s | 提交库存 |
| ReleaseInventoryCommand | inventory.commands | inventory-service | skuId | 30s | 释放库存 |
| CreatePaymentIntentCommand | payment.commands | payment-service | orderId | 60s | 创建支付意图 |
| ProcessRefundCommand | payment.commands | payment-service | orderId | 300s | 处理退款 |

### 支付领域 (Payment Domain)

#### 发布的事件 (Published by Payment Service)
| 事件类型 | 主题 | 分区键 | 序列化 | 兼容模式 | SLA | 描述 |
|----------|------|--------|--------|----------|-----|------|
| PaymentIntentCreated | payment.events | intentId | JSON | Full | P95<100ms | 支付意图创建 |
| PaymentInitiated | payment.events | intentId | JSON | Full | P95<100ms | 支付发起 |
| PaymentSucceeded | payment.events | intentId | JSON | Full | P95<100ms | 支付成功 |
| PaymentFailed | payment.events | intentId | JSON | Full | P95<100ms | 支付失败 |
| RefundRequested | payment.events | intentId | JSON | Full | P95<100ms | 退款请求 |
| RefundSucceeded | payment.events | intentId | JSON | Full | P95<100ms | 退款成功 |
| RefundFailed | payment.events | intentId | JSON | Full | P95<100ms | 退款失败 |

#### 消费的事件 (Consumed from Other Domains)
| 事件类型 | 来源主题 | 发布者 | 处理用途 | 重试策略 |
|----------|----------|--------|----------|----------|
| OrderCreated | order.events | order-service | 创建支付意图 | 指数退避，最大3次 |
| OrderCancelled | order.events | order-service | 取消未完成支付 | 指数退避，最大3次 |
| AfterSaleApproved | order.events | order-service | 处理退款申请 | 指数退避，最大5次 |

### 库存领域 (Inventory Domain)

#### 发布的事件 (Published by Inventory Service)
| 事件类型 | 主题 | 分区键 | 序列化 | 兼容模式 | SLA | 描述 |
|----------|------|--------|--------|----------|-----|------|
| InventoryReserved | inventory.events | skuId | JSON | Full | P95<50ms | 库存预占成功 |
| InventoryReservationFailed | inventory.events | skuId | JSON | Full | P95<50ms | 库存预占失败 |
| InventoryCommitted | inventory.events | skuId | JSON | Full | P95<50ms | 库存提交确认 |
| InventoryReleased | inventory.events | skuId | JSON | Full | P95<50ms | 库存释放确认 |
| InventoryAdjusted | inventory.events | skuId | JSON | Full | P95<100ms | 库存调整 |
| InventoryReplenished | inventory.events | skuId | JSON | Full | P95<100ms | 库存补货 |

#### 消费的命令 (Consumed Commands)
| 命令类型 | 来源主题 | 发布者 | 处理用途 | 重试策略 |
|----------|----------|--------|----------|----------|
| ReserveInventoryCommand | inventory.commands | order-service | 执行库存预占 | 不重试，同步响应 |
| CommitInventoryCommand | inventory.commands | order-service | 执行库存提交 | 不重试，同步响应 |
| ReleaseInventoryCommand | inventory.commands | order-service | 执行库存释放 | 不重试，同步响应 |

### 用户领域 (User Domain)

#### 发布的事件 (Published by User Service)
| 事件类型 | 主题 | 分区键 | 序列化 | 兼容模式 | SLA | 描述 |
|----------|------|--------|--------|----------|-----|------|
| UserRegistered | user.events | userId | JSON | Full | P95<100ms | 用户注册 |
| UserProfileUpdated | user.events | userId | JSON | Full | P95<100ms | 用户信息更新 |
| UserStatusChanged | user.events | userId | JSON | Full | P95<100ms | 用户状态变更 |

### 商品领域 (Product Domain)

#### 发布的事件 (Published by Product Service)
| 事件类型 | 主题 | 分区键 | 序列化 | 兼容模式 | SLA | 描述 |
|----------|------|--------|--------|----------|-----|------|
| ProductCreated | product.events | productId | JSON | Full | P95<100ms | 商品创建 |
| ProductUpdated | product.events | productId | JSON | Full | P95<100ms | 商品更新 |
| ProductStatusChanged | product.events | productId | JSON | Full | P95<100ms | 商品状态变更 |
| SkuCreated | product.events | skuId | JSON | Full | P95<100ms | SKU 创建 |
| SkuUpdated | product.events | skuId | JSON | Full | P95<100ms | SKU 更新 |

## 分区策略

### 分区键选择原则
- **聚合一致性**：同一聚合的事件使用相同分区键
- **负载均衡**：避免热点键导致分区倾斜
- **有序保证**：需要顺序的事件使用相同分区键

### 热点处理策略
- **一致性哈希**：高频 SKU 使用一致性哈希分桶
- **动态分区**：监控分区负载，动态调整
- **预分桶**：热点 SKU 预先分配到多个分区

### 分区配置
```properties
# 订单事件主题
order.events.partitions=12
order.events.replication.factor=3
order.events.partition.key=orderId

# 支付事件主题  
payment.events.partitions=8
payment.events.replication.factor=3
payment.events.partition.key=intentId

# 库存事件主题
inventory.events.partitions=8  
inventory.events.replication.factor=3
inventory.events.partition.key=skuId
```

## DLQ 与错误处理

### DLQ 主题命名
格式：`{原主题名}.dlq`

例如：
- `order.events.dlq`
- `payment.events.dlq`  
- `inventory.commands.dlq`

### 错误处理策略
| 错误类型 | 处理策略 | 重试次数 | DLQ |
|----------|----------|----------|-----|
| 序列化错误 | 直接进入 DLQ | 0 | 是 |
| 业务逻辑错误 | 指数退避重试 | 3-5 | 是 |
| 临时网络错误 | 指数退避重试 | 5-10 | 是 |
| 幂等性冲突 | 忽略（已处理） | 0 | 否 |

### DLQ 处理流程
1. **错误分类**：分析错误原因和影响
2. **修复数据**：修复数据格式或逻辑问题
3. **重新投递**：将修复后的消息重新发送
4. **监控告警**：DLQ 积压超过阈值告警

## 监控与运维

### 关键指标
- `topic_message_rate`：主题消息生产/消费速率
- `partition_lag`：分区消费延迟
- `dlq_message_count`：DLQ 消息数量
- `event_processing_time`：事件处理时间
- `duplicate_event_rate`：重复事件率

### 告警规则
- 消费延迟超过 5 分钟
- DLQ 消息数量超过 100
- 分区负载不均衡（最大最小差值 > 30%）
- 事件处理失败率超过 1%

## 容量规划

### 消息量估算
| 主题 | TPS (正常) | TPS (峰值) | 日消息量 | 消息大小 | 日存储量 |
|------|------------|------------|----------|----------|----------|
| order.events | 100 | 1000 | 8.6M | 2KB | 17GB |
| payment.events | 150 | 1500 | 13M | 1KB | 13GB |
| inventory.events | 200 | 2000 | 17M | 1KB | 17GB |

### 存储保留策略
- **短期事件**：7天（用户行为、会话事件）
- **业务事件**：30天（订单、支付、库存）
- **审计事件**：永久保留（关键业务操作）
- **冷存储**：30天后迁移到对象存储

## 相关文档
- [事件信封](./event-envelope.md)
- [消费者拓扑](./consumer-topology.md)
- [Schema Registry](./schema-registry.md)
- [DLQ 回放 Runbook](../operations/runbooks/dlq-replay.md)
