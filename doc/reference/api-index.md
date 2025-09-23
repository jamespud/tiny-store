# API 索引

本文档提供系统中所有 API 接口的统一索引，按角色域和服务分类。所有接口遵循统一的路径规范和版本策略。

## 路径规范
格式：`/api/v{版本}/{角色域}/{资源}`
- 详细规范：[API 契约治理](../api/README.md)
- 术语定义：[术语表](./glossary.md)

## 用户接口 (User APIs)

### 购物车服务
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/user/carts/items` | 添加商品到购物车 | [OpenAPI](../api/openapi/cart-service/user-api.yaml) |
| PUT | `/api/v1/user/carts/items/{skuId}` | 更新购物车商品数量 | [OpenAPI](../api/openapi/cart-service/user-api.yaml) |
| GET | `/api/v1/user/carts` | 获取购物车内容 | [OpenAPI](../api/openapi/cart-service/user-api.yaml) |
| DELETE | `/api/v1/user/carts/items/{skuId}` | 删除购物车商品 | [OpenAPI](../api/openapi/cart-service/user-api.yaml) |

### 订单服务
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/user/orders` | 创建订单 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |
| GET | `/api/v1/user/orders` | 获取用户订单列表 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |
| GET | `/api/v1/user/orders/{orderId}` | 获取订单详情 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |
| POST | `/api/v1/user/orders/{orderId}/cancel` | 取消订单 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |
| POST | `/api/v1/user/orders/{orderId}/confirm` | 确认收货 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |
| POST | `/api/v1/user/orders/{orderId}/after-sale` | 申请售后 | [OpenAPI](../api/openapi/order-service/user-api.yaml) |

### 支付服务
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/user/payments/intents` | 创建支付意图 | [OpenAPI](../api/openapi/payment-service/user-api.yaml) |
| GET | `/api/v1/user/payments/intents/{intentId}` | 获取支付状态 | [OpenAPI](../api/openapi/payment-service/user-api.yaml) |

### 商品服务
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| GET | `/api/v1/user/products` | 搜索商品 | [OpenAPI](../api/openapi/product-service/user-api.yaml) |
| GET | `/api/v1/user/products/{productId}` | 获取商品详情 | [OpenAPI](../api/openapi/product-service/user-api.yaml) |

## 商家接口 (Merchant APIs)

### 订单管理
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| GET | `/api/v1/merchant/orders` | 获取店铺订单列表 | [OpenAPI](../api/openapi/order-service/merchant-api.yaml) |
| GET | `/api/v1/merchant/orders/{orderId}` | 获取订单详情 | [OpenAPI](../api/openapi/order-service/merchant-api.yaml) |
| POST | `/api/v1/merchant/orders/{orderId}/accept` | 接受订单 | [OpenAPI](../api/openapi/order-service/merchant-api.yaml) |
| POST | `/api/v1/merchant/orders/{orderId}/ship` | 订单发货 | [OpenAPI](../api/openapi/order-service/merchant-api.yaml) |
| POST | `/api/v1/merchant/orders/{orderId}/delivered` | 确认妥投 | [OpenAPI](../api/openapi/order-service/merchant-api.yaml) |

### 商品管理
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/merchant/products` | 创建商品 | [OpenAPI](../api/openapi/product-service/merchant-api.yaml) |
| PUT | `/api/v1/merchant/products/{productId}` | 更新商品 | [OpenAPI](../api/openapi/product-service/merchant-api.yaml) |
| GET | `/api/v1/merchant/products` | 获取店铺商品列表 | [OpenAPI](../api/openapi/product-service/merchant-api.yaml) |

## 管理接口 (Admin APIs)

### 订单管理
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| GET | `/api/v1/admin/orders` | 获取所有订单（分页） | [OpenAPI](../api/openapi/order-service/admin-api.yaml) |
| PUT | `/api/v1/admin/orders/{orderId}/status` | 手动调整订单状态 | [OpenAPI](../api/openapi/order-service/admin-api.yaml) |

### 用户管理
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| GET | `/api/v1/admin/users` | 获取用户列表 | [OpenAPI](../api/openapi/user-service/admin-api.yaml) |
| PUT | `/api/v1/admin/users/{userId}/status` | 用户状态管理 | [OpenAPI](../api/openapi/user-service/admin-api.yaml) |

## 内部接口 (Internal APIs)

### 支付回调
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/internal/payments/webhooks/wechat` | 微信支付回调 | [OpenAPI](../api/openapi/payment-service/internal-api.yaml) |
| POST | `/api/v1/internal/payments/webhooks/alipay` | 支付宝回调 | [OpenAPI](../api/openapi/payment-service/internal-api.yaml) |

### 物流回调
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| POST | `/api/v1/internal/orders/logistics-pickup` | 物流揽收回调 | [OpenAPI](../api/openapi/order-service/internal-api.yaml) |
| POST | `/api/v1/internal/orders/logistics-delivered` | 物流妥投回调 | [OpenAPI](../api/openapi/order-service/internal-api.yaml) |

### 库存查询
| 方法 | 路径 | 描述 | 契约位置 |
|------|------|------|----------|
| GET | `/api/v1/internal/inventory/availability` | 查询库存可用性 | [OpenAPI](../api/openapi/inventory-service/internal-api.yaml) |
| POST | `/api/v1/internal/inventory/reserve` | 预占库存 | [OpenAPI](../api/openapi/inventory-service/internal-api.yaml) |
| POST | `/api/v1/internal/inventory/commit` | 提交库存 | [OpenAPI](../api/openapi/inventory-service/internal-api.yaml) |
| POST | `/api/v1/internal/inventory/release` | 释放库存 | [OpenAPI](../api/openapi/inventory-service/internal-api.yaml) |

## 内部 RPC 接口 (gRPC - 可选)

| 服务 | 描述 | Proto 文件 |
|------|------|------------|
| OrderService | 订单域内部接口 | [order.proto](../api/proto/order/order_service.proto) |
| PaymentService | 支付域内部接口 | [payment.proto](../api/proto/payment/payment_service.proto) |
| InventoryService | 库存域内部接口 | [inventory.proto](../api/proto/inventory/inventory_service.proto) |

## 事件接口 (AsyncAPI)

| 主题 | 描述 | 契约位置 |
|------|------|----------|
| order.events | 订单领域事件 | [AsyncAPI](../api/asyncapi/order-events.yaml) |
| payment.events | 支付领域事件 | [AsyncAPI](../api/asyncapi/payment-events.yaml) |
| inventory.events | 库存领域事件 | [AsyncAPI](../api/asyncapi/inventory-events.yaml) |

## 接口分类说明

### 对外接口 (External)
- 用户接口、商家接口、管理接口
- 需要认证和授权
- 有限流和监控
- 遵循向后兼容性保证

### 内部接口 (Internal)
- 服务间调用、第三方回调
- 内部认证机制（mTLS、API Key）
- 更严格的性能要求
- 可以有破坏性变更（需内部协调）

### 事件接口 (Event-driven)
- 异步消息通信
- 事件溯源和 CQRS 支持
- Schema 版本化管理
- 幂等性保证

## 契约管理

所有 API 契约文件统一存放在 `doc/api/` 目录下，按服务和接口类型分类。契约变更需要经过：

1. **兼容性检查**：自动化工具检测破坏性变更
2. **影响评估**：评估对现有客户端的影响
3. **审查流程**：技术和业务双重审查
4. **版本发布**：按版本策略发布新版本

详细的契约治理流程请参考：[API 契约治理](../api/README.md)
