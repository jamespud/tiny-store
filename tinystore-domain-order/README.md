# 订单模块双阶段下单实现

## 概述

本实现按照"确认订单 -> 提交订单"双阶段模式重构了订单下单流程，支持：

- 预览阶段：生成商品快照、价格汇总、预订单令牌、摘要验证
- 提交阶段：幂等控制、摘要校验、价格重算、库存扣减、订单创建
- 安全防护：用户隔离、防篡改、防重放
- 错误处理：统一异常码、清晰错误信息

## 主要接口

### 1. 确认订单 (预览阶段)
```
POST /confirm
Content-Type: application/json

Request: SettlementRequest
Response: SettlementPreviewResult (含 preOrderToken, digest, idempotencyKey)
```

### 2. 提交订单 (提交阶段)  
```
POST /submit
Content-Type: application/json

Request: SubmitOrderCommand
Response: OrderCreatedResult (含 orderId, paymentIntentId, reused 标志)
```

## 核心组件

### Application Services
- `ConfirmSettlementService`: 预览阶段处理
- `PlaceOrderService`: 提交阶段处理  
- `PricingService`: 价格计算逻辑
- `DigestService`: 摘要生成与校验
- `IdempotencyService`: 幂等控制接口
- `PreOrderTokenStore`: 预订单令牌存储接口

### Infrastructure
- `RedisIdempotencyStore`: Redis 幂等存储实现
- `RedisPreOrderStore`: Redis 预订单存储实现

### Error Handling
- `OrderErrorCode`: 统一错误码枚举
- `OrderBusinessException`: 业务异常类
- `OrderExceptionHandler`: 全局异常处理器

## 配置

```yaml
order:
  digest:
    secret: ${ORDER_DIGEST_SECRET:default-digest-secret-key}
  preorder:
    ttl-seconds: 900  # 预订单令牌15分钟有效期
  idempotency:
    ttl-seconds: 600  # 幂等键10分钟有效期
```

## 安全特性

1. **摘要校验**: 使用 HMAC-SHA256 对订单金额和商品明细生成摘要，防篡改
2. **用户隔离**: 预订单令牌与用户ID绑定，防跨用户重放
3. **幂等控制**: 原子获取幂等锁，支持重复提交返回相同结果
4. **TTL过期**: 预订单令牌和幂等键自动过期，减少存储压力

## 测试

基础测试框架已搭建在 `src/test/java` 目录下。完整测试需要：

1. 配置嵌入式 Redis 或 Redis Mock
2. Mock ProductDomainService  
3. 集成测试覆盖幂等、价格变化、库存不足等场景

## 已实现清单

✅ 核心 DTO 定义 (SettlementRequest, SubmitOrderCommand, OrderCreatedResult)
✅ 错误处理体系 (OrderErrorCode, OrderBusinessException, ExceptionHandler)  
✅ 预览阶段服务 (ConfirmSettlementService)
✅ 提交阶段服务 (PlaceOrderService)
✅ 价格计算服务 (PricingService)
✅ 摘要计算服务 (DigestService)
✅ Redis 存储实现 (IdempotencyStore, PreOrderTokenStore)
✅ Controller 更新 (新接口 + 兼容旧接口)
✅ 配置属性 (application.yml)
✅ 测试框架搭建

## 待完善项

- [ ] 完整单元测试和集成测试
- [ ] 真实库存扣减与回滚逻辑
- [ ] 优惠券核销逻辑  
- [ ] 订单聚合与持久化
- [ ] 领域事件发布
- [ ] 支付意图集成
- [ ] 价格差异处理策略
- [ ] 监控指标接入
