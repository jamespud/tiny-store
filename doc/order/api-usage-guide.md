# 订单服务 API 使用指南
> 对齐提示：订单域分册 `../domain/order-es.md` 与 Saga `../architecture/saga-checkout.md` 提供状态、事件与编排的完整语义参考；读模型见 `../readmodels/order-summary.md`。

## 概述

订单服务提供了完整的订单生命周期管理，包括订单创建、状态流转、支付处理、物流跟踪等功能。本文档详细说明各API端点的使用方法和最佳实践。

## 状态机概述

订单状态基于CoreFlowStatus枚举，支持以下主要状态流转：

```
待支付 → 支付确认 → 待履约 → 履约中 → 已完成
      ↓           ↓        ↓       ↓
    已取消    ← 取消中  ←   售后中  ← 已退款
```

### 终态状态
- `COMPLETED`: 订单正常完成
- `CANCELLED`: 订单已取消
- `CLOSED`: 风控关闭
- `REFUNDED`: 已退款

## API 端点详解

### 1. 用户订单接口 (UserOrderController)

#### 1.1 创建订单
```http
POST /api/v1/user/orders
Content-Type: application/json
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}

{
  "products": [
    {
      "skuId": "sku_001",
      "quantity": 2,
      "price": 99.99
    }
  ],
  "deliveryAddress": {
    "province": "北京",
    "city": "北京市",
    "district": "朝阳区",
    "detail": "xxx街道xxx号"
  }
}
```

**幂等性说明**：
- 必须提供 `X-Idempotency-Key` 确保订单创建的幂等性
- 相同幂等性键的重复请求将返回相同结果
- `X-Correlation-ID` 用于链路追踪，建议提供

#### 1.2 取消订单
```http
POST /api/v1/user/orders/{orderId}/cancel
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}

{
  "reason": "用户主动取消"
}
```

**状态约束**：
- 仅支持 `PENDING_PAYMENT`、`AWAITING_FULFILLMENT`、`FULFILLING` 状态的订单取消
- 已发货订单取消需要进入 `CANCELLING` 状态等待商家确认

#### 1.3 确认收货
```http
POST /api/v1/user/orders/{orderId}/confirm
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}
```

**状态约束**：
- 仅支持 `FULFILLING` 状态的订单确认收货
- 确认后订单状态变更为 `COMPLETED`

### 2. 商家订单接口 (MerchantOrderController)

#### 2.1 接受订单
```http
POST /api/v1/merchant/orders/{orderId}/accept
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}
```

**状态约束**：
- 仅支持 `AWAITING_FULFILLMENT` 状态的订单
- 商家接受后订单保持 `AWAITING_FULFILLMENT` 状态

#### 2.2 发货
```http
POST /api/v1/merchant/orders/{orderId}/ship
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}

{
  "trackingNumber": "SF123456789",
  "carrier": "顺丰速运"
}
```

**状态约束**：
- 仅支持 `AWAITING_FULFILLMENT` 状态的订单
- 发货后订单状态变更为 `FULFILLING`

#### 2.3 确认妥投
```http
POST /api/v1/merchant/orders/{orderId}/delivered
X-Idempotency-Key: {unique-key}
X-Correlation-ID: {correlation-id}
```

### 3. 内部回调接口 (InternalOrderController)

#### 3.1 支付成功回调
```http
POST /api/v1/internal/orders/payment-success
Content-Type: application/json
X-Signature: {payment-service-signature}

{
  "orderId": "order_12345",
  "paymentId": "pay_67890",
  "amount": 199.98,
  "currency": "CNY",
  "eventId": "event_unique_id"
}
```

**安全性**：
- 需要验证支付服务的签名 (X-Signature)
- eventId 用于确保回调的幂等性

#### 3.2 物流状态回调
```http
POST /api/v1/internal/orders/logistics-pickup
POST /api/v1/internal/orders/logistics-delivered

{
  "orderId": "order_12345",
  "trackingNumber": "SF123456789",
  "status": "PICKED_UP",
  "timestamp": "2025-09-22T10:30:00Z",
  "eventId": "logistics_event_id"
}
```

## 幂等性机制

### 幂等性键 (X-Idempotency-Key)
- **格式**: 建议使用 UUID 或业务唯一标识
- **作用域**: 24小时内有效
- **行为**: 相同键的重复请求返回缓存结果
- **示例**: `user_12345_cancel_20250922103000`

### 关联ID (X-Correlation-ID)
- **用途**: 跨服务链路追踪
- **格式**: UUID 格式
- **传播**: 自动传播到下游服务调用
- **日志**: 所有日志记录包含此ID

## 错误处理

### 状态转换错误
```json
{
  "error": "ILLEGAL_STATE_TRANSITION",
  "message": "无法从 COMPLETED 状态转换到 CANCELLED",
  "currentStatus": "COMPLETED",
  "targetStatus": "CANCELLED",
  "orderId": "order_12345"
}
```

### 幂等性错误
```json
{
  "error": "IDEMPOTENCY_CONFLICT",
  "message": "幂等性键已存在但请求内容不同",
  "idempotencyKey": "key_12345"
}
```

### 权限错误
```json
{
  "error": "ACCESS_DENIED",
  "message": "订单不属于当前用户",
  "orderId": "order_12345"
}
```

## 最佳实践

### 1. 幂等性设计
- 所有状态变更操作必须提供幂等性键
- 使用业务相关的唯一标识作为幂等性键
- 重试时使用相同的幂等性键

### 2. 错误重试
- 网络错误：指数退避重试，最多3次
- 状态冲突：检查当前状态后决定是否重试
- 权限错误：不要重试

### 3. 状态检查
- 状态变更前先检查当前状态
- 使用乐观锁防止并发冲突
- 异常情况下查询最新状态

### 4. 日志和监控
- 所有请求包含关联ID用于链路追踪
- 监控状态转换成功率和延迟
- 设置关键业务指标告警

## 示例代码

### Java客户端示例
```java
// 创建订单
String idempotencyKey = "user_" + userId + "_create_" + System.currentTimeMillis();
String correlationId = UUID.randomUUID().toString();

CreateOrderRequest request = CreateOrderRequest.builder()
    .products(products)
    .deliveryAddress(address)
    .build();

CreateOrderResponse response = orderClient.createOrder(
    request, 
    idempotencyKey, 
    correlationId
);
```

### cURL示例
```bash
# 创建订单
curl -X POST "http://localhost:8080/api/v1/user/orders" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: user_12345_create_1695369000" \
  -H "X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "products": [{"skuId": "sku_001", "quantity": 1, "price": 99.99}],
    "deliveryAddress": {...}
  }'
```

## 状态查询

虽然当前版本暂未实现订单查询接口，但建议的查询参数设计如下：

```http
GET /api/v1/user/orders?status=FULFILLING&page=1&size=20
GET /api/v1/user/orders/{orderId}
```

预计在后续版本中提供完整的查询功能。